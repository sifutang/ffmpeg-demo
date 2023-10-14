# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.18.1)

# Declares and names the project.

project("ffplayer")

message("build ffplayer shared library")
message("CMAKE_ANDROID_ARCH_ABI: ${CMAKE_ANDROID_ARCH_ABI}")

# ffmpeg
set(ffmpeg_lib_dir ${CMAKE_SOURCE_DIR}/../libs/${CMAKE_ANDROID_ARCH_ABI})
set(ffmpeg_head_dir ${CMAKE_SOURCE_DIR}/vendor/ffmpeg)
include_directories(${ffmpeg_head_dir})

# libyuv
set(libyuv_lib_dir ${CMAKE_SOURCE_DIR}/../libs/${CMAKE_ANDROID_ARCH_ABI})
set(libyuv_head_dir ${CMAKE_SOURCE_DIR}/vendor/libyuv)
include_directories(${libyuv_head_dir}/libyuv.h)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library(
        avutil
        SHARED
        IMPORTED
)
set_target_properties(avutil
        PROPERTIES IMPORTED_LOCATION
        ${ffmpeg_lib_dir}/libavutil.so)

add_library( swresample
        SHARED
        IMPORTED )
set_target_properties( swresample
        PROPERTIES IMPORTED_LOCATION
        ${ffmpeg_lib_dir}/libswresample.so )

add_library( avcodec
        SHARED
        IMPORTED )
set_target_properties( avcodec
        PROPERTIES IMPORTED_LOCATION
        ${ffmpeg_lib_dir}/libavcodec.so )

add_library( avfilter
        SHARED
        IMPORTED)
set_target_properties( avfilter
        PROPERTIES IMPORTED_LOCATION
        ${ffmpeg_lib_dir}/libavfilter.so )

add_library( swscale
        SHARED
        IMPORTED)
set_target_properties( swscale
        PROPERTIES IMPORTED_LOCATION
        ${ffmpeg_lib_dir}/libswscale.so )

add_library( avformat
        SHARED
        IMPORTED)
set_target_properties( avformat
        PROPERTIES IMPORTED_LOCATION
        ${ffmpeg_lib_dir}/libavformat.so )

add_library(
        yuv
        SHARED
        IMPORTED
)
set_target_properties(yuv
        PROPERTIES IMPORTED_LOCATION
        ${libyuv_lib_dir}/libyuv.so)

add_library( # Sets the name of the library.
        ffplayer

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        ./decoder/BaseDecoder.cpp
        ./decoder/VideoDecoder.cpp
        ./decoder/AudioDecoder.cpp

        ./base/AVPacketQueue.cpp

        ./reader/FFReader.cpp
        ./reader/FFVideoReader.cpp

        ./writer/FFVideoWriter.cpp

        ./settings/CompileSettings.h

        filter/FFFilter.cpp

        utils/ImageDef.h
        utils/FFConverter.h
        utils/FFMpegUtils.cpp
        utils/MutexObj.cpp
        utils/TraceUtils.cpp

        main/FFMpegPlayer.cpp
        native-lib.cpp
        )

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

get_filename_component(module_main ${CMAKE_SOURCE_DIR} DIRECTORY)
get_filename_component(module_src ${module_main} DIRECTORY)
get_filename_component(module_root ${module_src} DIRECTORY)
get_filename_component(project_root ${module_root} DIRECTORY)
message("project_root: ${project_root}")
set(base_utils_header_root ${project_root}/libutils/src/main/cpp)

target_include_directories(ffplayer PRIVATE
        # base_utils search path
        ${base_utils_header_root}/jni
        ${base_utils_header_root}/utils
        )

target_link_libraries( # Specifies the target library.
        ffplayer

        # ffmpeg
        avutil
        swresample
        avcodec
        avfilter
        swscale
        avformat

        # libyuv
        yuv

        # system
        android
        ${log-lib})