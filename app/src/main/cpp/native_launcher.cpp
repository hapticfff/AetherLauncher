#include <jni.h>
#include <dlfcn.h>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>
#include <android/log.h>
#include <unistd.h>

using JLI_LaunchFn = int (*)(
    int, char **,
    int, const char **,
    int, const char **,
    const char *, const char *, const char *, const char *,
    jboolean, jboolean, jboolean, jint);

static std::string jstringToString(JNIEnv *env, jstring value) {
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_aetherlauncher_runtime_NativeJavaProcess_nativeLaunch(
    JNIEnv *env, jobject, jstring runtimeDirectory, jobjectArray arguments) {
    const std::string runtime = jstringToString(env, runtimeDirectory);
    const std::string javaExecutable = runtime + "/bin/java";
    const std::string jliPath = runtime + "/lib/jli/libjli.so";
    const std::string libPath = runtime + "/lib/jli:" + runtime + "/lib/server:" + runtime + "/lib";

    setenv("JAVA_HOME", runtime.c_str(), 1);
    setenv("LD_LIBRARY_PATH", libPath.c_str(), 1);

    const jsize count = env->GetArrayLength(arguments);
    std::vector<std::string> values;
    values.reserve(static_cast<size_t>(count) + 1);
    values.push_back(javaExecutable);
    for (jsize i = 0; i < count; ++i) {
        auto value = static_cast<jstring>(env->GetObjectArrayElement(arguments, i));
        values.push_back(jstringToString(env, value));
        env->DeleteLocalRef(value);
    }

    std::vector<char *> argv;
    argv.reserve(values.size() + 1);
    for (auto &value : values) argv.push_back(const_cast<char *>(value.c_str()));
    argv.push_back(nullptr);

    void *handle = dlopen(jliPath.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (!handle) {
        __android_log_print(ANDROID_LOG_ERROR, "AetherLauncher", "Unable to load libjli: %s", dlerror());
        return 127;
    }

    auto launch = reinterpret_cast<JLI_LaunchFn>(dlsym(handle, "JLI_Launch"));
    if (!launch) {
        __android_log_print(ANDROID_LOG_ERROR, "AetherLauncher", "JLI_Launch symbol missing: %s", dlerror());
        return 127;
    }

    __android_log_print(ANDROID_LOG_INFO, "AetherLauncher", "Calling JLI_Launch from %s", jliPath.c_str());
    const int result = launch(
        static_cast<int>(values.size()), argv.data(),
        0, nullptr,
        0, nullptr,
        "Aether Launcher", "Aether Launcher",
        "java", "java",
        JNI_FALSE, JNI_FALSE, JNI_FALSE, 0);

    dlclose(handle);
    return result;
}
