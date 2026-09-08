#include <jni.h>
#include <dlfcn.h>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>
#include <thread>
#include <chrono>
#include <android/log.h>
#include <unistd.h>
#include <dirent.h>
#include <sys/stat.h>

extern "C" bool aetherBindPojavSurface(JNIEnv* env);

using JLI_LaunchFn = int (*)(int, char **, int, const char **, int, const char **,
    const char *, const char *, const char *, const char *, jboolean, jboolean, jboolean, jint);

static std::string jstringToString(JNIEnv *env, jstring value) {
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return result;
}
static bool isRegularFile(const std::string &path) { struct stat info{}; return stat(path.c_str(), &info) == 0 && S_ISREG(info.st_mode); }
static std::string findLibraryRecursive(const std::string &root, const std::string &name) {
    DIR *directory = opendir(root.c_str()); if (!directory) return "";
    while (dirent *entry = readdir(directory)) {
        const char *entryName = entry->d_name;
        if (strcmp(entryName, ".") == 0 || strcmp(entryName, "..") == 0) continue;
        const std::string path = root + "/" + entryName;
        if (isRegularFile(path) && name == entryName) { closedir(directory); return path; }
        if (entry->d_type == DT_DIR || entry->d_type == DT_UNKNOWN) {
            const std::string found = findLibraryRecursive(path, name);
            if (!found.empty()) { closedir(directory); return found; }
        }
    }
    closedir(directory); return "";
}
static std::string findJliLibrary(const std::string &runtime) {
    const char *candidates[] = {"/lib/jli/libjli.so", "/lib/aarch64/jli/libjli.so", "/lib/arm64/jli/libjli.so", "/lib/arm/jli/libjli.so", "/lib/x86/jli/libjli.so", "/lib/x86_64/jli/libjli.so"};
    for (const char *candidate : candidates) { const std::string path = runtime + candidate; if (isRegularFile(path)) return path; }
    return findLibraryRecursive(runtime + "/lib", "libjli.so");
}
static void appendLibraryPath(std::string &value, const std::string &path) { if (value.empty()) value = path; else value += ":" + path; }
static void configureLibraryPath(const std::string &runtime, const std::string &jliPath) {
    std::string libPath; appendLibraryPath(libPath, runtime + "/lib/jli"); appendLibraryPath(libPath, runtime + "/lib/server"); appendLibraryPath(libPath, runtime + "/lib");
    const std::string marker = "/lib/"; const size_t markerPos = jliPath.find(marker);
    if (markerPos != std::string::npos) { const std::string relative = jliPath.substr(markerPos + marker.size()); const size_t slash = relative.find('/');
        if (slash != std::string::npos) { const std::string architectureRoot = runtime + "/lib/" + relative.substr(0, slash); appendLibraryPath(libPath, architectureRoot + "/jli"); appendLibraryPath(libPath, architectureRoot + "/server"); appendLibraryPath(libPath, architectureRoot); }
    }
    appendLibraryPath(libPath, "/system/lib64"); appendLibraryPath(libPath, "/vendor/lib64"); setenv("LD_LIBRARY_PATH", libPath.c_str(), 1);
}
static void preloadRuntimeLibrary(const std::string &runtime, const std::string &jliPath, const char *name, std::vector<void *> &handles) {
    std::string path = findLibraryRecursive(runtime + "/lib", name); if (path.empty() && strcmp(name, "libjli.so") == 0) path = jliPath; if (path.empty()) return;
    void *handle = dlopen(path.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (handle) { handles.push_back(handle); __android_log_print(ANDROID_LOG_INFO, "AetherLauncher", "Loaded %s from %s", name, path.c_str()); }
    else __android_log_print(ANDROID_LOG_WARN, "AetherLauncher", "Could not load %s from %s: %s", name, path.c_str(), dlerror());
}

static void waitForPojavSurfaceBinding(JavaVM* vm) {
    for (int attempt = 0; attempt < 600; ++attempt) {
        void* handle = dlopen("libpojavexec.so", RTLD_NOW | RTLD_NOLOAD);
        if (handle) {
            JNIEnv* attachedEnv = nullptr;
            if (vm->AttachCurrentThread(&attachedEnv, nullptr) == JNI_OK && aetherBindPojavSurface(attachedEnv)) {
                __android_log_print(ANDROID_LOG_INFO, "AetherLauncher", "Android Surface bound to libpojavexec after child JVM load");
                vm->DetachCurrentThread();
                return;
            }
            if (attachedEnv) vm->DetachCurrentThread();
            return;
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(50));
    }
    __android_log_print(ANDROID_LOG_WARN, "AetherLauncher", "Timed out waiting for libpojavexec Surface handoff");
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_aetherlauncher_runtime_NativeJavaProcess_nativeLaunch(
    JNIEnv *env, jobject, jstring runtimeDirectory, jobjectArray arguments, jstring renderer) {
    const std::string runtime = jstringToString(env, runtimeDirectory);
    const std::string selectedRenderer = jstringToString(env, renderer);
    const std::string javaExecutable = runtime + "/bin/java";
    const std::string jliPath = findJliLibrary(runtime);
    if (jliPath.empty()) { __android_log_print(ANDROID_LOG_ERROR, "AetherLauncher", "libjli.so was not found under %s/lib", runtime.c_str()); return 127; }

    setenv("JAVA_HOME", runtime.c_str(), 1); configureLibraryPath(runtime, jliPath);
    setenv("PATH", (runtime + "/bin:" + (getenv("PATH") ? getenv("PATH") : "")).c_str(), 1);
    if (!selectedRenderer.empty()) {
        setenv("AETHER_RENDERER", selectedRenderer.c_str(), 1);
        if (selectedRenderer == "OpenGL") { setenv("POJAV_RENDERER", "opengles3", 1); setenv("LIBGL_ES", "3", 1); }
        else if (selectedRenderer == "Vulkan") { setenv("POJAV_RENDERER", "opengles3_desktopgl_angle_vulkan", 1); unsetenv("LIBGL_ES"); }
        else { unsetenv("AETHER_RENDERER"); unsetenv("POJAV_RENDERER"); unsetenv("LIBGL_ES"); }
    }

    std::vector<void *> handles;
    preloadRuntimeLibrary(runtime, jliPath, "libjli.so", handles); preloadRuntimeLibrary(runtime, jliPath, "libjvm.so", handles);
    preloadRuntimeLibrary(runtime, jliPath, "libverify.so", handles); preloadRuntimeLibrary(runtime, jliPath, "libjava.so", handles);
    preloadRuntimeLibrary(runtime, jliPath, "libnet.so", handles); preloadRuntimeLibrary(runtime, jliPath, "libnio.so", handles);
    preloadRuntimeLibrary(runtime, jliPath, "libawt.so", handles); preloadRuntimeLibrary(runtime, jliPath, "libawt_headless.so", handles);
    preloadRuntimeLibrary(runtime, jliPath, "libfreetype.so", handles); preloadRuntimeLibrary(runtime, jliPath, "libfontmanager.so", handles);
    preloadRuntimeLibrary(runtime, jliPath, "libzip.so", handles);

    void *jliHandle = nullptr;
    for (void *handle : handles) if (dlsym(handle, "JLI_Launch") != nullptr) { jliHandle = handle; break; }
    if (!jliHandle) { for (void *handle : handles) dlclose(handle); return 127; }
    auto launch = reinterpret_cast<JLI_LaunchFn>(dlsym(jliHandle, "JLI_Launch"));
    if (!launch) { for (void *handle : handles) dlclose(handle); return 127; }

    JavaVM* parentVm = nullptr; env->GetJavaVM(&parentVm);
    std::thread surfaceBinder(waitForPojavSurfaceBinding, parentVm);

    const jsize count = env->GetArrayLength(arguments);
    std::vector<std::string> values; values.reserve(static_cast<size_t>(count) + 1); values.push_back(javaExecutable);
    for (jsize i = 0; i < count; ++i) { auto value = static_cast<jstring>(env->GetObjectArrayElement(arguments, i)); values.push_back(jstringToString(env, value)); env->DeleteLocalRef(value); }
    std::vector<char *> argv; argv.reserve(values.size() + 1); for (auto &value : values) argv.push_back(const_cast<char *>(value.c_str())); argv.push_back(nullptr);

    __android_log_print(ANDROID_LOG_INFO, "AetherLauncher", "Calling JLI_Launch from %s", jliPath.c_str());
    const int result = launch(static_cast<int>(values.size()), argv.data(), 0, nullptr, 0, nullptr,
        "Aether Launcher", "Aether Launcher", "java", "java", JNI_FALSE, JNI_FALSE, JNI_FALSE, 0);

    surfaceBinder.join();
    for (void *handle : handles) dlclose(handle);
    return result;
}
