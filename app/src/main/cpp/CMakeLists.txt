cmake_minimum_required(VERSION 3.22.1)

project("gdal_reader")

# Path to pre-compiled GDAL headers
# User should provide these in src/main/cpp/include
include_directories(include)

add_library(gdal_reader SHARED gdal_reader.cpp)

# Find libraries provided by Android
find_library(log-lib log)

# Import GDAL shared library
# Assuming user places libgdal.so in src/main/jniLibs/${ANDROID_ABI}/
add_library(gdal SHARED IMPORTED)
set_target_properties(gdal PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libgdal.so)

target_link_libraries(gdal_reader
        gdal
        ${log-lib})
