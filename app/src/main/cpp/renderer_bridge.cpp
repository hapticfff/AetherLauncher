#include <jni.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>
#include <dlfcn.h>
#include <mutex>
#include <string>
#include <vector>
#include <chrono>
#include <cstring>

#define TAG "AetherRenderer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct GLFWmonitor {
    int unused = 0;
};

struct GLFWvidmode {
    int width;
    int height;
    int redBits;
    int greenBits;
    int blueBits;
    int refreshRate;
};

struct AetherGlfwWindow {
    int width = 1;
    int height = 1;
    bool shouldClose = false;
    std::string title;
    ANativeWindow* window = nullptr;
    EGLDisplay display = EGL_NO_DISPLAY;
    EGLSurface surface = EGL_NO_SURFACE;
    EGLContext context = EGL_NO_CONTEXT;
};

static std::mutex gMutex;
static std::string gStatus = "Renderer bridge idle";
static ANativeWindow* gAndroidWindow = nullptr;
static EGLDisplay gDisplay = EGL_NO_DISPLAY;
static EGLConfig gConfig = nullptr;
static EGLContext gContext = EGL_NO_CONTEXT;
static AetherGlfwWindow* gCurrentWindow = nullptr;
static GLFWmonitor gPrimaryMonitor;
static GLFWvidmode gVideoMode{1920, 1080, 8, 8, 8, 60};
static std::chrono::steady_clock::time_point gTimerStart = std::chrono::steady_clock::now();

static bool createEglContextLocked() {
    if (!gAndroidWindow) return false;
    if (gDisplay != EGL_NO_DISPLAY && gConfig != nullptr && gContext != EGL_NO_CONTEXT) return true;

    gDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (gDisplay == EGL_NO_DISPLAY || !eglInitialize(gDisplay, nullptr, nullptr)) {
        LOGE("EGL initialization failed");
        gDisplay = EGL_NO_DISPLAY;
        return false;
    }

    const EGLint configAttrs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_NONE
    };
    EGLint count = 0;
    if (!eglChooseConfig(gDisplay, configAttrs, &gConfig, 1, &count) || count == 0) {
        LOGE("No EGL ES 3 config available");
        eglTerminate(gDisplay);
        gDisplay = EGL_NO_DISPLAY;
        gConfig = nullptr;
        return false;
    }

    const EGLint contextAttrs[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    gContext = eglCreateContext(gDisplay, gConfig, EGL_NO_CONTEXT, contextAttrs);
    if (gContext == EGL_NO_CONTEXT) {
        LOGE("EGL ES 3 context creation failed: 0x%x", eglGetError());
        eglTerminate(gDisplay);
        gDisplay = EGL_NO_DISPLAY;
        gConfig = nullptr;
        return false;
    }

    return true;
}

static void destroyEglContextLocked() {
    if (gDisplay != EGL_NO_DISPLAY) {
        eglMakeCurrent(gDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (gContext != EGL_NO_CONTEXT) eglDestroyContext(gDisplay, gContext);
        eglTerminate(gDisplay);
    }
    gDisplay = EGL_NO_DISPLAY;
    gConfig = nullptr;
    gContext = EGL_NO_CONTEXT;
    gCurrentWindow = nullptr;
}

static bool attachAndroidWindow(ANativeWindow* window) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (!window) return false;

    if (gAndroidWindow) ANativeWindow_release(gAndroidWindow);
    gAndroidWindow = window;
    ANativeWindow_acquire(gAndroidWindow);

    if (!createEglContextLocked()) {
        gStatus = "OpenGL ES 3 EGL initialization failed";
        ANativeWindow_release(gAndroidWindow);
        gAndroidWindow = nullptr;
        return false;
    }

    const GLubyte* version = nullptr;
    const GLubyte* vendor = nullptr;
    EGLSurface surface = eglCreateWindowSurface(gDisplay, gConfig, gAndroidWindow, nullptr);
    if (surface == EGL_NO_SURFACE || !eglMakeCurrent(gDisplay, surface, surface, gContext)) {
        LOGE("Initial EGL surface creation failed: 0x%x", eglGetError());
        if (surface != EGL_NO_SURFACE) eglDestroySurface(gDisplay, surface);
        gStatus = "OpenGL ES window surface initialization failed";
        return false;
    }
    version = glGetString(GL_VERSION);
    vendor = glGetString(GL_VENDOR);
    LOGI("OpenGL ES initialized: %s / %s",
         version ? reinterpret_cast<const char*>(version) : "unknown",
         vendor ? reinterpret_cast<const char*>(vendor) : "unknown");
    glViewport(0, 0, ANativeWindow_getWidth(gAndroidWindow), ANativeWindow_getHeight(gAndroidWindow));
    glClearColor(0.03f, 0.02f, 0.08f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    eglSwapBuffers(gDisplay, surface);
    eglDestroySurface(gDisplay, surface);
    eglMakeCurrent(gDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);

    gStatus = "OpenGL ES 3 Android window ready for LWJGL/GLFW";
    return true;
}

static bool validateVulkan(ANativeWindow* window) {
    void* vulkanLib = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (!vulkanLib) {
        LOGE("Vulkan library unavailable: %s", dlerror());
        return false;
    }

    auto createInstance = reinterpret_cast<PFN_vkCreateInstance>(dlsym(vulkanLib, "vkCreateInstance"));
    auto destroyInstance = reinterpret_cast<PFN_vkDestroyInstance>(dlsym(vulkanLib, "vkDestroyInstance"));
    auto getInstanceProcAddr = reinterpret_cast<PFN_vkGetInstanceProcAddr>(dlsym(vulkanLib, "vkGetInstanceProcAddr"));
    if (!createInstance || !destroyInstance || !getInstanceProcAddr) {
        dlclose(vulkanLib);
        return false;
    }

    VkApplicationInfo appInfo{};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "Aether Launcher";
    appInfo.applicationVersion = 1;
    appInfo.pEngineName = "Aether Renderer";
    appInfo.engineVersion = 1;
    appInfo.apiVersion = VK_API_VERSION_1_0;

    const char* extensions[] = {VK_KHR_SURFACE_EXTENSION_NAME, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME};
    VkInstanceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &appInfo;
    createInfo.enabledExtensionCount = 2;
    createInfo.ppEnabledExtensionNames = extensions;

    VkInstance instance = VK_NULL_HANDLE;
    VkResult result = createInstance(&createInfo, nullptr, &instance);
    if (result != VK_SUCCESS) {
        dlclose(vulkanLib);
        return false;
    }

    auto createSurface = reinterpret_cast<PFN_vkCreateAndroidSurfaceKHR>(getInstanceProcAddr(instance, "vkCreateAndroidSurfaceKHR"));
    auto destroySurface = reinterpret_cast<PFN_vkDestroySurfaceKHR>(getInstanceProcAddr(instance, "vkDestroySurfaceKHR"));
    auto enumeratePhysicalDevices = reinterpret_cast<PFN_vkEnumeratePhysicalDevices>(getInstanceProcAddr(instance, "vkEnumeratePhysicalDevices"));
    if (!createSurface || !destroySurface || !enumeratePhysicalDevices) {
        destroyInstance(instance, nullptr);
        dlclose(vulkanLib);
        return false;
    }

    VkAndroidSurfaceCreateInfoKHR surfaceInfo{};
    surfaceInfo.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
    surfaceInfo.window = window;
    VkSurfaceKHR surface = VK_NULL_HANDLE;
    result = createSurface(instance, &surfaceInfo, nullptr, &surface);
    if (result != VK_SUCCESS) {
        destroyInstance(instance, nullptr);
        dlclose(vulkanLib);
        return false;
    }

    uint32_t deviceCount = 0;
    result = enumeratePhysicalDevices(instance, &deviceCount, nullptr);
    LOGI("Vulkan validation: physical devices=%u", deviceCount);
    destroySurface(instance, surface, nullptr);
    destroyInstance(instance, nullptr);
    dlclose(vulkanLib);
    return result == VK_SUCCESS && deviceCount > 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_aetherlauncher_renderer_RendererNative_attachSurface(JNIEnv* env, jobject, jobject surface, jstring renderer) {
    const char* rendererChars = env->GetStringUTFChars(renderer, nullptr);
    std::string requested = rendererChars ? rendererChars : "Auto";
    if (rendererChars) env->ReleaseStringUTFChars(renderer, rendererChars);

    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        std::lock_guard<std::mutex> lock(gMutex);
        gStatus = "Android surface unavailable";
        return JNI_FALSE;
    }

    if (requested == "Vulkan") {
        const bool ok = validateVulkan(window);
        if (ok) {
            // Phase 4.3 GLFW currently targets the Android OpenGL ES path.
            // Keep Vulkan validation separate until the LWJGL Vulkan path is added.
            std::lock_guard<std::mutex> lock(gMutex);
            gStatus = "Vulkan Android surface validated; LWJGL GLFW path is OpenGL ES for Phase 4.3";
        }
        ANativeWindow_release(window);
        return ok ? JNI_TRUE : JNI_FALSE;
    }

    const bool ok = attachAndroidWindow(window);
    ANativeWindow_release(window);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_aetherlauncher_renderer_RendererNative_detachSurface(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(gMutex);
    destroyEglContextLocked();
    if (gAndroidWindow) {
        ANativeWindow_release(gAndroidWindow);
        gAndroidWindow = nullptr;
    }
    gStatus = "Renderer surface detached";
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_aetherlauncher_renderer_RendererNative_rendererStatus(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(gMutex);
    return env->NewStringUTF(gStatus.c_str());
}

// -----------------------------------------------------------------------------
// Phase 4.3: minimal GLFW 3 native ABI backed by the Android SurfaceView.
// LWJGL's generated GLFW bindings resolve these exported function names from
// the configured native library. This avoids shipping desktop Linux GLFW.
// -----------------------------------------------------------------------------
extern "C" int glfwInit() {
    std::lock_guard<std::mutex> lock(gMutex);
    if (!gAndroidWindow) {
        gStatus = "GLFW init waiting for Android surface";
        return 0;
    }
    gTimerStart = std::chrono::steady_clock::now();
    return createEglContextLocked() ? 1 : 0;
}

extern "C" void glfwTerminate() {
    std::lock_guard<std::mutex> lock(gMutex);
    if (gCurrentWindow) {
        delete gCurrentWindow;
        gCurrentWindow = nullptr;
    }
    destroyEglContextLocked();
}

extern "C" void glfwDefaultWindowHints() {}
extern "C" void glfwWindowHint(int, int) {}
extern "C" void glfwInitHint(int, int) {}
extern "C" const char* glfwGetVersionString() { return "Aether GLFW Android ES3 bridge"; }
extern "C" void glfwGetVersion(int* major, int* minor, int* revision) {
    if (major) *major = 3;
    if (minor) *minor = 3;
    if (revision) *revision = 0;
}
extern "C" int glfwGetError(const char**) { return 0; }
extern "C" void* glfwSetErrorCallback(void* callback) { return callback; }

extern "C" void* glfwCreateWindow(int width, int height, const char* title, void*, void*) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (!gAndroidWindow || !createEglContextLocked()) return nullptr;
    if (gCurrentWindow) delete gCurrentWindow;

    auto* result = new AetherGlfwWindow();
    result->width = width > 0 ? width : ANativeWindow_getWidth(gAndroidWindow);
    result->height = height > 0 ? height : ANativeWindow_getHeight(gAndroidWindow);
    result->title = title ? title : "Minecraft";
    result->window = gAndroidWindow;
    ANativeWindow_acquire(result->window);
    result->display = gDisplay;
    result->context = gContext;
    result->surface = eglCreateWindowSurface(gDisplay, gConfig, result->window, nullptr);
    if (result->surface == EGL_NO_SURFACE) {
        ANativeWindow_release(result->window);
        delete result;
        return nullptr;
    }
    gCurrentWindow = result;
    eglMakeCurrent(gDisplay, result->surface, result->surface, gContext);
    return result;
}

extern "C" void glfwDestroyWindow(void* window) {
    std::lock_guard<std::mutex> lock(gMutex);
    auto* result = static_cast<AetherGlfwWindow*>(window);
    if (!result) return;
    if (result->display != EGL_NO_DISPLAY && result->surface != EGL_NO_SURFACE) {
        eglMakeCurrent(result->display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroySurface(result->display, result->surface);
    }
    if (result->window) ANativeWindow_release(result->window);
    if (gCurrentWindow == result) gCurrentWindow = nullptr;
    delete result;
}

extern "C" int glfwWindowShouldClose(void* window) {
    auto* result = static_cast<AetherGlfwWindow*>(window);
    return result && result->shouldClose ? 1 : 0;
}
extern "C" void glfwSetWindowShouldClose(void* window, int value) {
    auto* result = static_cast<AetherGlfwWindow*>(window);
    if (result) result->shouldClose = value != 0;
}
extern "C" void glfwSetWindowTitle(void* window, const char* title) {
    auto* result = static_cast<AetherGlfwWindow*>(window);
    if (result && title) result->title = title;
}
extern "C" const char* glfwGetWindowTitle(void* window) {
    auto* result = static_cast<AetherGlfwWindow*>(window);
    return result ? result->title.c_str() : "";
}
extern "C" void glfwMakeContextCurrent(void* window) {
    std::lock_guard<std::mutex> lock(gMutex);
    auto* result = static_cast<AetherGlfwWindow*>(window);
    if (!result) {
        if (gDisplay != EGL_NO_DISPLAY) eglMakeCurrent(gDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        return;
    }
    eglMakeCurrent(result->display, result->surface, result->surface, result->context);
}
extern "C" void* glfwGetCurrentContext() { return gCurrentWindow; }
extern "C" void glfwSwapBuffers(void* window) {
    auto* result = static_cast<AetherGlfwWindow*>(window);
    if (result) eglSwapBuffers(result->display, result->surface);
}
extern "C" void glfwSwapInterval(int interval) {
    if (gDisplay != EGL_NO_DISPLAY) eglSwapInterval(gDisplay, interval);
}
extern "C" void glfwPollEvents() {}
extern "C" void glfwWaitEvents() {}
extern "C" void glfwWaitEventsTimeout(double) {}
extern "C" void glfwPostEmptyEvent() {}

extern "C" void glfwGetWindowSize(void* window, int* width, int* height) {
    auto* result = static_cast<AetherGlfwWindow*>(window);
    if (width) *width = result ? result->width : 0;
    if (height) *height = result ? result->height : 0;
}
extern "C" void glfwGetFramebufferSize(void* window, int* width, int* height) {
    auto* result = static_cast<AetherGlfwWindow*>(window);
    if (result && result->window) {
        if (width) *width = ANativeWindow_getWidth(result->window);
        if (height) *height = ANativeWindow_getHeight(result->window);
    } else {
        if (width) *width = 0;
        if (height) *height = 0;
    }
}
extern "C" void glfwSetWindowSize(void* window, int width, int height) {
    auto* result = static_cast<AetherGlfwWindow*>(window);
    if (result) {
        result->width = width;
        result->height = height;
    }
}
extern "C" void glfwGetWindowPos(void*, int* x, int* y) { if (x) *x = 0; if (y) *y = 0; }
extern "C" void glfwSetWindowPos(void*, int, int) {}
extern "C" void glfwShowWindow(void*) {}
extern "C" void glfwHideWindow(void*) {}
extern "C" void glfwFocusWindow(void*) {}
extern "C" void glfwIconifyWindow(void*) {}
extern "C" void glfwRestoreWindow(void*) {}
extern "C" void glfwMaximizeWindow(void*) {}
extern "C" int glfwGetWindowAttrib(void*, int) { return 1; }
extern "C" void glfwSetWindowAttrib(void*, int, int) {}
extern "C" void glfwSetWindowOpacity(void*, float) {}
extern "C" float glfwGetWindowOpacity(void*) { return 1.0f; }

extern "C" void* glfwGetPrimaryMonitor() { return &gPrimaryMonitor; }
extern "C" void* glfwGetWindowMonitor(void*) { return nullptr; }
extern "C" void* glfwGetMonitors(int* count) {
    static void* monitors[] = {&gPrimaryMonitor};
    if (count) *count = 1;
    return monitors;
}
extern "C" const GLFWvidmode* glfwGetVideoMode(void*) { return &gVideoMode; }
extern "C" void glfwGetMonitorPos(void*, int* x, int* y) { if (x) *x = 0; if (y) *y = 0; }
extern "C" const char* glfwGetMonitorName(void*) { return "Android display"; }
extern "C" void glfwSetGamma(void*, float) {}
extern "C" void glfwSetGammaRamp(void*, const void*) {}
extern "C" const void* glfwGetGammaRamp(void*) { return nullptr; }

extern "C" double glfwGetTime() {
    return std::chrono::duration<double>(std::chrono::steady_clock::now() - gTimerStart).count();
}
extern "C" void glfwSetTime(double time) { gTimerStart = std::chrono::steady_clock::now() - std::chrono::duration_cast<std::chrono::steady_clock::duration>(std::chrono::duration<double>(time)); }
extern "C" uint64_t glfwGetTimerValue() { return static_cast<uint64_t>(glfwGetTime() * 1000000000.0); }
extern "C" uint64_t glfwGetTimerFrequency() { return 1000000000ULL; }

extern "C" void glfwSetInputMode(void*, int, int) {}
extern "C" int glfwGetInputMode(void*, int) { return 0; }
extern "C" int glfwGetKey(void*, int) { return 0; }
extern "C" int glfwGetMouseButton(void*, int) { return 0; }
extern "C" void glfwGetCursorPos(void*, double* x, double* y) { if (x) *x = 0.0; if (y) *y = 0.0; }
extern "C" void glfwSetCursorPos(void*, double, double) {}
extern "C" void glfwSetCursor(void*, void*) {}
extern "C" void* glfwCreateStandardCursor(int) { return nullptr; }
extern "C" void glfwDestroyCursor(void*) {}

// LWJGL callback setters are intentionally no-ops in this first Android window bridge.
#define AETHER_CALLBACK(name) extern "C" void* name(void*, void* callback) { return callback; }
AETHER_CALLBACK(glfwSetWindowPosCallback)
AETHER_CALLBACK(glfwSetWindowSizeCallback)
AETHER_CALLBACK(glfwSetWindowCloseCallback)
AETHER_CALLBACK(glfwSetWindowRefreshCallback)
AETHER_CALLBACK(glfwSetWindowFocusCallback)
AETHER_CALLBACK(glfwSetWindowIconifyCallback)
AETHER_CALLBACK(glfwSetFramebufferSizeCallback)
AETHER_CALLBACK(glfwSetKeyCallback)
AETHER_CALLBACK(glfwSetCharCallback)
AETHER_CALLBACK(glfwSetCharModsCallback)
AETHER_CALLBACK(glfwSetMouseButtonCallback)
AETHER_CALLBACK(glfwSetCursorPosCallback)
AETHER_CALLBACK(glfwSetCursorEnterCallback)
AETHER_CALLBACK(glfwSetScrollCallback)
AETHER_CALLBACK(glfwSetDropCallback)

extern "C" int glfwJoystickPresent(int) { return 0; }
extern "C" const float* glfwGetJoystickAxes(int, int* count) { if (count) *count = 0; return nullptr; }
extern "C" const unsigned char* glfwGetJoystickButtons(int, int* count) { if (count) *count = 0; return nullptr; }
extern "C" const char* glfwGetJoystickName(int) { return nullptr; }
extern "C" void glfwSetClipboardString(void*, const char*) {}
extern "C" const char* glfwGetClipboardString(void*) { return ""; }
extern "C" int glfwExtensionSupported(const char*) { return 0; }
extern "C" void* glfwGetProcAddress(const char* name) {
    if (!name) return nullptr;
    void* address = reinterpret_cast<void*>(eglGetProcAddress(name));
    if (address) return address;
    static void* gles = nullptr;
    if (!gles) gles = dlopen("libGLESv3.so", RTLD_NOW | RTLD_LOCAL);
    return gles ? dlsym(gles, name) : nullptr;
}
