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

#define TAG "AetherRenderer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::mutex gMutex;
static std::string gStatus = "Renderer bridge idle";

static bool renderOpenGL(ANativeWindow* window) {
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY || !eglInitialize(display, nullptr, nullptr)) {
        LOGE("EGL initialization failed");
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
    EGLConfig config = nullptr;
    EGLint count = 0;
    if (!eglChooseConfig(display, configAttrs, &config, 1, &count) || count == 0) {
        LOGE("No EGL ES 3 config available");
        eglTerminate(display);
        return false;
    }

    const EGLint contextAttrs[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
    EGLContext context = eglCreateContext(display, config, EGL_NO_CONTEXT, contextAttrs);
    if (context == EGL_NO_CONTEXT) {
        LOGE("EGL ES 3 context creation failed: 0x%x", eglGetError());
        eglTerminate(display);
        return false;
    }

    EGLSurface surface = eglCreateWindowSurface(display, config, window, nullptr);
    if (surface == EGL_NO_SURFACE || !eglMakeCurrent(display, surface, surface, context)) {
        LOGE("EGL window surface creation failed: 0x%x", eglGetError());
        if (surface != EGL_NO_SURFACE) eglDestroySurface(display, surface);
        eglDestroyContext(display, context);
        eglTerminate(display);
        return false;
    }

    const GLubyte* version = glGetString(GL_VERSION);
    const GLubyte* vendor = glGetString(GL_VENDOR);
    LOGI("OpenGL ES initialized: %s / %s", version ? reinterpret_cast<const char*>(version) : "unknown", vendor ? reinterpret_cast<const char*>(vendor) : "unknown");

    // Phase 4.2 validates the real Android GLES/EGL path. The Minecraft/LWJGL frame loop will bind here next.
    glViewport(0, 0, ANativeWindow_getWidth(window), ANativeWindow_getHeight(window));
    glClearColor(0.03f, 0.02f, 0.08f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    eglSwapBuffers(display, surface);

    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroySurface(display, surface);
    eglDestroyContext(display, context);
    eglTerminate(display);
    return true;
}

static bool validateVulkan(ANativeWindow* window) {
    // Vulkan is API 24+, while this app intentionally keeps minSdk 23.
    // Load libvulkan dynamically so the API-23 build does not require a
    // link-time Vulkan library that is absent from the API-23 NDK sysroot.
    void* vulkanLib = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (!vulkanLib) {
        LOGE("Vulkan library unavailable: %s", dlerror());
        return false;
    }

    auto createInstance = reinterpret_cast<PFN_vkCreateInstance>(dlsym(vulkanLib, "vkCreateInstance"));
    auto destroyInstance = reinterpret_cast<PFN_vkDestroyInstance>(dlsym(vulkanLib, "vkDestroyInstance"));
    auto getInstanceProcAddr = reinterpret_cast<PFN_vkGetInstanceProcAddr>(dlsym(vulkanLib, "vkGetInstanceProcAddr"));
    if (!createInstance || !destroyInstance || !getInstanceProcAddr) {
        LOGE("Required Vulkan entry points are unavailable");
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

    const char* extensions[] = { VK_KHR_SURFACE_EXTENSION_NAME, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME };
    VkInstanceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &appInfo;
    createInfo.enabledExtensionCount = 2;
    createInfo.ppEnabledExtensionNames = extensions;

    VkInstance instance = VK_NULL_HANDLE;
    VkResult result = createInstance(&createInfo, nullptr, &instance);
    if (result != VK_SUCCESS) {
        LOGE("Vulkan instance creation failed: %d", result);
        dlclose(vulkanLib);
        return false;
    }

    auto createSurface = reinterpret_cast<PFN_vkCreateAndroidSurfaceKHR>(
        getInstanceProcAddr(instance, "vkCreateAndroidSurfaceKHR"));
    auto destroySurface = reinterpret_cast<PFN_vkDestroySurfaceKHR>(
        getInstanceProcAddr(instance, "vkDestroySurfaceKHR"));
    auto enumeratePhysicalDevices = reinterpret_cast<PFN_vkEnumeratePhysicalDevices>(
        getInstanceProcAddr(instance, "vkEnumeratePhysicalDevices"));
    if (!createSurface || !destroySurface || !enumeratePhysicalDevices) {
        LOGE("Vulkan Android surface functions are unavailable");
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
        LOGE("Vulkan Android surface creation failed: %d", result);
        destroyInstance(instance, nullptr);
        dlclose(vulkanLib);
        return false;
    }

    uint32_t deviceCount = 0;
    result = enumeratePhysicalDevices(instance, &deviceCount, nullptr);
    const bool gpuAvailable = result == VK_SUCCESS && deviceCount > 0;
    LOGI("Vulkan initialized: physical devices=%u", deviceCount);

    destroySurface(instance, surface, nullptr);
    destroyInstance(instance, nullptr);
    dlclose(vulkanLib);
    return gpuAvailable;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_aetherlauncher_renderer_RendererNative_attachSurface(
    JNIEnv* env, jobject, jobject surface, jstring renderer) {
    const char* rendererChars = env->GetStringUTFChars(renderer, nullptr);
    std::string requested = rendererChars ? rendererChars : "Auto";
    if (rendererChars) env->ReleaseStringUTFChars(renderer, rendererChars);

    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        std::lock_guard<std::mutex> lock(gMutex);
        gStatus = "Android surface unavailable";
        return JNI_FALSE;
    }

    bool ok = false;
    if (requested == "Vulkan") {
        ok = validateVulkan(window);
        std::lock_guard<std::mutex> lock(gMutex);
        gStatus = ok ? "Vulkan Android surface ready" : "Vulkan initialization failed";
    } else {
        ok = renderOpenGL(window);
        std::lock_guard<std::mutex> lock(gMutex);
        gStatus = ok ? "OpenGL ES 3 EGL surface ready" : "OpenGL ES initialization failed";
    }

    ANativeWindow_release(window);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_aetherlauncher_renderer_RendererNative_detachSurface(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(gMutex);
    gStatus = "Renderer surface detached";
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_aetherlauncher_renderer_RendererNative_rendererStatus(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(gMutex);
    return env->NewStringUTF(gStatus.c_str());
}
