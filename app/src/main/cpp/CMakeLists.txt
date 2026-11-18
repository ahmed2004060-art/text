cmake_minimum_required(VERSION 3.22.1)
project(ortbridge LANGUAGES CXX)

if(NOT EXISTS "${ORT_HEADERS_DIR}/onnxruntime_c_api.h")
    message(FATAL_ERROR "ONNX Runtime headers not found in: ${ORT_HEADERS_DIR}")
endif()

add_library(ortbridge SHARED bridge.cpp)
target_include_directories(ortbridge PRIVATE "${ORT_HEADERS_DIR}")
target_compile_features(ortbridge PRIVATE cxx_std_17)
target_link_libraries(ortbridge dl log)
