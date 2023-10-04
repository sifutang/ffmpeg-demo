/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * https://android.googlesource.com/platform/libnativehelper/+/7d26526/JNIHelp.c
 */
#ifndef NATIVEHELPER_JNIHELP_H_
#define NATIVEHELPER_JNIHELP_H_

#include <jni.h>
#include "../../utils/Logger.h"

/*
 * Get a human-readable summary of an exception object.  The buffer will
 * be populated with the "binary" class name and, if present, the
 * exception message.
 */
static void getExceptionSummary(JNIEnv* env, jthrowable excep, char* buf, size_t bufLen) {
    if (excep == NULL) {
        return;
    }
    /* get the name of the exception's class; none of these should fail */
    jclass clazz = env->GetObjectClass(excep); // exception's class
    jclass jlc = env->GetObjectClass(clazz);   // java.lang.Class
    jmethodID getNameMethod = env->GetMethodID(jlc, "getName", "()Ljava/lang/String;");
    jstring className = (jstring)env->CallObjectMethod(clazz, getNameMethod);
    /* get printable string */
    const char* nameStr = env->GetStringUTFChars(className, NULL);
    if (nameStr == NULL) {
        snprintf(buf, bufLen, "%s", "out of memory generating summary");
        env->ExceptionClear();  // clear OOM
        return;
    }
    /* if the exception has a message string, get that */
    jmethodID getThrowableMessage = env->GetMethodID(clazz, "getMessage", "()Ljava/lang/String;");
    jstring message = (jstring)env->CallObjectMethod(excep, getThrowableMessage);
    if (message != NULL) {
        const char* messageStr = env->GetStringUTFChars(message, NULL);
        snprintf(buf, bufLen, "%s: %s", nameStr, messageStr);
        if (messageStr != NULL) {
            env->ReleaseStringUTFChars(message, messageStr);
        } else {
            env->ExceptionClear();        // clear OOM
        }
    } else {
        strncpy(buf, nameStr, bufLen);
        buf[bufLen-1] = '\0';
    }
    env->ReleaseStringUTFChars(className, nameStr);
}

/*
 * Throw an exception with the specified class and an optional message.
 *
 * If an exception is currently pending, we log a warning message and
 * clear it.
 *
 * Returns 0 if the specified exception was successfully thrown.  (Some
 * sort of exception will always be pending when this returns.)
 */
static int jniThrowException(JNIEnv* env, const char* className, const char* msg) {
    jclass exceptionClass;
    if (env->ExceptionCheck()) {
        /* TODO: consider creating the new exception with this as "cause" */
        char buf[256];
        jthrowable excep = env->ExceptionOccurred();
        env->ExceptionClear();
        getExceptionSummary(env, excep, buf, sizeof(buf));
        LOGW("Discarding pending exception (%s) to throw %s\n", buf, className);
    }
    exceptionClass = env->FindClass(className);
    if (exceptionClass == NULL) {
        LOGE("Unable to find exception class %s\n", className);
        /* ClassNotFoundException now pending */
        return -1;
    }
    if (env->ThrowNew(exceptionClass, msg) != JNI_OK) {
        LOGE("Failed throwing '%s' '%s'\n", className, msg);
        /* an exception, most likely OOM, will now be pending */
        return -1;
    }
    return 0;
}

/*
 * Throw a java.lang.NullPointerException, with an optional message.
 */
static int jniThrowNullPointerException(JNIEnv* env, const char* msg) {
    return jniThrowException(env, "java/lang/NullPointerException", msg);
}

#endif  /* NATIVEHELPER_JNIHELP_H_ */
