#include <jni.h>
#include <android/keycodes.h>
#include <dlfcn.h>
#include <mutex>

using SetupBridgeWindowFn = void (*)(JNIEnv*, jclass, jobject);
using ReleaseBridgeWindowFn = void (*)(JNIEnv*, jclass);
using SendKeyFn = void (*)(jint, jint, jint, jint);
using SendCharFn = jboolean (*)(jchar);
using SendCursorFn = void (*)(jfloat, jfloat);
using SendMouseFn = void (*)(jint, jint, jint);
using SendScrollFn = void (*)(jdouble, jdouble);
using SendScreenFn = void (*)(jint, jint);

static std::mutex gMutex;
static jobject gSurface = nullptr;
static void* gPojavExec = nullptr;
static SetupBridgeWindowFn gSetupBridgeWindow = nullptr;
static ReleaseBridgeWindowFn gReleaseBridgeWindow = nullptr;
static SendKeyFn gSendKey = nullptr;
static SendCharFn gSendChar = nullptr;
static SendCursorFn gSendCursor = nullptr;
static SendMouseFn gSendMouse = nullptr;
static SendScrollFn gSendScroll = nullptr;
static SendScreenFn gSendScreen = nullptr;

static bool resolvePojavLocked() {
    if (gPojavExec) return true;
    gPojavExec = dlopen("libpojavexec.so", RTLD_NOW | RTLD_GLOBAL);
    if (!gPojavExec) return false;
    gSetupBridgeWindow = reinterpret_cast<SetupBridgeWindowFn>(dlsym(gPojavExec, "Java_net_kdt_pojavlaunch_utils_JREUtils_setupBridgeWindow"));
    gReleaseBridgeWindow = reinterpret_cast<ReleaseBridgeWindowFn>(dlsym(gPojavExec, "Java_net_kdt_pojavlaunch_utils_JREUtils_releaseBridgeWindow"));
    gSendKey = reinterpret_cast<SendKeyFn>(dlsym(gPojavExec, "JavaCritical_org_lwjgl_glfw_CallbackBridge_nativeSendKey"));
    gSendChar = reinterpret_cast<SendCharFn>(dlsym(gPojavExec, "JavaCritical_org_lwjgl_glfw_CallbackBridge_nativeSendChar"));
    gSendCursor = reinterpret_cast<SendCursorFn>(dlsym(gPojavExec, "JavaCritical_org_lwjgl_glfw_CallbackBridge_nativeSendCursorPos"));
    gSendMouse = reinterpret_cast<SendMouseFn>(dlsym(gPojavExec, "JavaCritical_org_lwjgl_glfw_CallbackBridge_nativeSendMouseButton"));
    gSendScroll = reinterpret_cast<SendScrollFn>(dlsym(gPojavExec, "JavaCritical_org_lwjgl_glfw_CallbackBridge_nativeSendScroll"));
    gSendScreen = reinterpret_cast<SendScreenFn>(dlsym(gPojavExec, "JavaCritical_org_lwjgl_glfw_CallbackBridge_nativeSendScreenSize"));
    return gSetupBridgeWindow != nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_aetherlauncher_renderer_PojavInputNative_bindSurface(JNIEnv* env, jobject, jobject surface) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (gSurface) env->DeleteGlobalRef(gSurface);
    gSurface = surface ? env->NewGlobalRef(surface) : nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_aetherlauncher_renderer_PojavInputNative_unbindSurface(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (resolvePojavLocked() && gReleaseBridgeWindow && gSurface) gReleaseBridgeWindow(env, nullptr);
    if (gSurface) env->DeleteGlobalRef(gSurface);
    gSurface = nullptr;
}

extern "C" bool aetherBindPojavSurface(JNIEnv* env) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (!gSurface || !resolvePojavLocked() || !gSetupBridgeWindow) return false;
    gSetupBridgeWindow(env, nullptr, gSurface);
    return true;
}

static int androidKeyToGlfw(int keyCode) {
    switch (keyCode) {
        case AKEYCODE_ESCAPE: return 256; case AKEYCODE_ENTER: return 257; case AKEYCODE_TAB: return 258;
        case AKEYCODE_DEL: return 259; case AKEYCODE_INSERT: return 260; case AKEYCODE_FORWARD_DEL: return 261;
        case AKEYCODE_DPAD_RIGHT: return 262; case AKEYCODE_DPAD_LEFT: return 263; case AKEYCODE_DPAD_DOWN: return 264; case AKEYCODE_DPAD_UP: return 265;
        case AKEYCODE_PAGE_UP: return 266; case AKEYCODE_PAGE_DOWN: return 267; case AKEYCODE_MOVE_HOME: return 268; case AKEYCODE_MOVE_END: return 269;
        case AKEYCODE_SPACE: return 32; case AKEYCODE_MINUS: return 45; case AKEYCODE_EQUALS: return 61;
        case AKEYCODE_LEFT_BRACKET: return 91; case AKEYCODE_RIGHT_BRACKET: return 93; case AKEYCODE_BACKSLASH: return 92;
        case AKEYCODE_SEMICOLON: return 59; case AKEYCODE_APOSTROPHE: return 39; case AKEYCODE_COMMA: return 44;
        case AKEYCODE_PERIOD: return 46; case AKEYCODE_SLASH: return 47; case AKEYCODE_GRAVE: return 96;
        case AKEYCODE_SHIFT_LEFT: return 340; case AKEYCODE_SHIFT_RIGHT: return 344; case AKEYCODE_CTRL_LEFT: return 341; case AKEYCODE_CTRL_RIGHT: return 345;
        case AKEYCODE_ALT_LEFT: return 342; case AKEYCODE_ALT_RIGHT: return 346; case AKEYCODE_META_LEFT: return 343; case AKEYCODE_META_RIGHT: return 347;
        case AKEYCODE_F1: return 290; case AKEYCODE_F2: return 291; case AKEYCODE_F3: return 292; case AKEYCODE_F4: return 293;
        case AKEYCODE_F5: return 294; case AKEYCODE_F6: return 295; case AKEYCODE_F7: return 296; case AKEYCODE_F8: return 297;
        case AKEYCODE_F9: return 298; case AKEYCODE_F10: return 299; case AKEYCODE_F11: return 300; case AKEYCODE_F12: return 301;
        default:
            if (keyCode >= AKEYCODE_A && keyCode <= AKEYCODE_Z) return 'A' + (keyCode - AKEYCODE_A);
            if (keyCode >= AKEYCODE_0 && keyCode <= AKEYCODE_9) return '0' + (keyCode - AKEYCODE_0);
            return -1;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_aetherlauncher_renderer_PojavInputNative_sendTouch(JNIEnv*, jobject, jint action, jfloat x, jfloat y, jint) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (!resolvePojavLocked()) return;
    if (gSendCursor) gSendCursor(x, y);
    if (gSendMouse && action != 2) gSendMouse(0, action == 0 ? 1 : 0, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_aetherlauncher_renderer_PojavInputNative_sendKey(JNIEnv*, jobject, jint keyCode, jint scanCode, jint action, jint metaState) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (!resolvePojavLocked() || !gSendKey) return;
    const int key = androidKeyToGlfw(keyCode);
    if (key >= 0) gSendKey(key, scanCode, action, metaState);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_aetherlauncher_renderer_PojavInputNative_sendChar(JNIEnv*, jobject, jint codePoint) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (!resolvePojavLocked() || !gSendChar) return;
    if (codePoint >= 0 && codePoint <= 0xFFFF) gSendChar(static_cast<jchar>(codePoint));
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_aetherlauncher_renderer_PojavInputNative_sendScroll(JNIEnv*, jobject, jfloat horizontal, jfloat vertical) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (!resolvePojavLocked() || !gSendScroll) return;
    gSendScroll(horizontal, vertical);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_aetherlauncher_renderer_PojavInputNative_sendResize(JNIEnv*, jobject, jint width, jint height) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (!resolvePojavLocked() || !gSendScreen) return;
    gSendScreen(width, height);
}
