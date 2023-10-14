//
// Created by 雪月清的随笔 on 19/4/23.
//

#include "../utils/TraceUtils.h"
#include <mutex>
#include "header/Logger.h"

fp_ATrace_beginSection TraceUtils::m_fpATraceBeginSection = nullptr;
fp_ATrace_endSection TraceUtils::m_fpATraceEndSection = nullptr;

void TraceUtils::init() {
#ifdef ENABLE_TRACE
    static std::once_flag s_onceFlag;
    std::call_once(s_onceFlag, []() {
        // Retrieve a handle to libandroid.
        void *lib = dlopen("libandroid.so", RTLD_NOW || RTLD_LOCAL);

        // Access the native tracing functions.
        if (lib != nullptr) {
            // Use dlsym() to prevent crashes on devices running Android 5.1
            // (API level 22) or lower.
            m_fpATraceBeginSection = reinterpret_cast<fp_ATrace_beginSection>(
                    dlsym(lib, "ATrace_beginSection"));
            m_fpATraceEndSection = reinterpret_cast<fp_ATrace_endSection>(
                    dlsym(lib, "ATrace_endSection"));
        }
        LOGI("TraceUtils::init success: %d", m_fpATraceBeginSection != nullptr)
    });
#endif
}

void TraceUtils::beginSection(const std::string &sectionName) {
#ifdef ENABLE_TRACE
    if (m_fpATraceBeginSection) {
        m_fpATraceBeginSection(sectionName.c_str());
    }
#endif
}

void TraceUtils::endSection() {
#ifdef ENABLE_TRACE
    if (m_fpATraceEndSection) {
        m_fpATraceEndSection();
    }
#endif
}
