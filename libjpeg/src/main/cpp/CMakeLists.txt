# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.18.1)

# Declares and names the project.

project("jpeg_helper")

message("build libjpeg shared library")
message("CMAKE_ANDROID_ARCH_ABI: ${CMAKE_ANDROID_ARCH_ABI}")

# libjpeg
set(libjpeg_lib_dir ${CMAKE_SOURCE_DIR}/../libs/${CMAKE_ANDROID_ARCH_ABI})
set(libjpeg_head_dir ${CMAKE_SOURCE_DIR}/source)
include_directories(${libjpeg_head_dir})

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library(
        turbojpeg
        SHARED
        IMPORTED
)
set_target_properties(turbojpeg
        PROPERTIES IMPORTED_LOCATION
        ${libjpeg_lib_dir}/libturbojpeg.so)

add_library( jpeg
        SHARED
        IMPORTED )
set_target_properties( jpeg
        PROPERTIES IMPORTED_LOCATION
        ${libjpeg_lib_dir}/libjpeg.so )

add_library( # Sets the name of the library.
        jpeg_helper

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
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

target_include_directories(jpeg_helper PRIVATE
        # base_utils search path
        ${base_utils_header_root}/jni
        ${base_utils_header_root}/utils
        )

target_link_libraries( # Specifies the target library.
        jpeg_helper

        # jpeg
        jpeg
        turbojpeg

        #system
        android
        ${log-lib})