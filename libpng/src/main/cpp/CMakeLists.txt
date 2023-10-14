# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.18.1)

# Declares and names the project.

project("png_helper")
message("build png shared library")
message("CMAKE_ANDROID_ARCH_ABI: ${CMAKE_ANDROID_ARCH_ABI}")

add_library(cpufeatures STATIC
        ${ANDROID_NDK}/sources/android/cpufeatures/cpu-features.c)

set(PNGLIB_MAJOR 1)
set(PNGLIB_MINOR 6)
set(PNGLIB_REVISION 39)
set(PNGLIB_SUBREVISION 0)
set(PNGLIB_NAME libpng${PNGLIB_MAJOR}${PNGLIB_MINOR})
set(PNGLIB_VERSION ${PNGLIB_MAJOR}.${PNGLIB_MINOR}.${PNGLIB_REVISION})
set(PNGLIB_SHARED_SOVERSION ${PNGLIB_MAJOR}${PNGLIB_MINOR})
set(PNGLIB_SHARED_VERSION ${PNGLIB_SHARED_SOVERSION}.${PNGLIB_REVISION}.${PNGLIB_SUBREVISION})


set(libpng_source_dir ${CMAKE_SOURCE_DIR}/source)

# COMMAND LINE OPTIONS
option(PNG_HARDWARE_OPTIMIZATIONS "Enable hardware optimizations" ON)

if (PNG_HARDWARE_OPTIMIZATIONS)

    # set definitions and sources for arm
    if (CMAKE_SYSTEM_PROCESSOR MATCHES "^arm" OR
            CMAKE_SYSTEM_PROCESSOR MATCHES "^aarch64")
        set(libpng_arm_sources
                ${libpng_source_dir}/arm/arm_init.c
                ${libpng_source_dir}/arm/filter_neon.S
                ${libpng_source_dir}/arm/filter_neon_intrinsics.c
                ${libpng_source_dir}/arm/palette_neon_intrinsics.c)

        add_definitions(-DPNG_ARM_NEON_OPT=2)

    endif ()
else (PNG_HARDWARE_OPTIMIZATIONS)

    if (CMAKE_SYSTEM_PROCESSOR MATCHES "^arm" OR
            CMAKE_SYSTEM_PROCESSOR MATCHES "^aarch64")
        add_definitions(-DPNG_ARM_NEON_OPT=0)
    endif ()

endif (PNG_HARDWARE_OPTIMIZATIONS)

set_property(SOURCE ${libpng_arm_sources}
        APPEND_STRING PROPERTY COMPILE_FLAGS " -mfpu=neon")

if (NOT AWK OR ANDROID)
    # No awk available to generate sources; use pre-built pnglibconf.h
    configure_file(${libpng_source_dir}/scripts/pnglibconf.h.prebuilt
            ${libpng_source_dir}/pnglibconf.h)
    add_custom_target(genfiles) # Dummy
endif ()

include_directories(${CMAKE_CURRENT_BINARY_DIR})

set(libpng_public_hdrs
        ${libpng_source_dir}/png.h
        ${libpng_source_dir}/pngconf.h
        ${libpng_source_dir}/pnglibconf.h
        )

set(libpng_private_hdrs
        ${libpng_source_dir}/pngpriv.h
        ${libpng_source_dir}/pngdebug.h
        ${libpng_source_dir}/pnginfo.h
        ${libpng_source_dir}/pngstruct.h
        )

set(libpng_sources
        ${libpng_public_hdrs}
        ${libpng_private_hdrs}

        ${libpng_source_dir}/png.c
        ${libpng_source_dir}/pngerror.c
        ${libpng_source_dir}/pngget.c
        ${libpng_source_dir}/pngmem.c
        ${libpng_source_dir}/pngpread.c
        ${libpng_source_dir}/pngread.c
        ${libpng_source_dir}/pngrio.c
        ${libpng_source_dir}/pngrtran.c
        ${libpng_source_dir}/pngrutil.c
        ${libpng_source_dir}/pngset.c
        ${libpng_source_dir}/pngtrans.c
        ${libpng_source_dir}/pngwio.c
        ${libpng_source_dir}/pngwrite.c
        ${libpng_source_dir}/pngwtran.c
        ${libpng_source_dir}/pngwutil.c

        ${libpng_arm_sources}

        ./native-lib.cpp
        )

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

add_library(
        png_helper

        SHARED

        ${libpng_sources})

get_filename_component(module_main ${CMAKE_SOURCE_DIR} DIRECTORY)
get_filename_component(module_src ${module_main} DIRECTORY)
get_filename_component(module_root ${module_src} DIRECTORY)
get_filename_component(project_root ${module_root} DIRECTORY)
message("project_root: ${project_root}")
set(base_utils_header_root ${project_root}/libutils/src/main/cpp)

target_include_directories(png_helper PRIVATE
        ${ANDROID_NDK}/sources/android/cpufeatures

        # base_utils search path
        ${base_utils_header_root}/jni
        ${base_utils_header_root}/utils
        )

target_link_libraries(
        png_helper

        # system
        z
        ${log-lib}
        cpufeatures)