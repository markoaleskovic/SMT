cmake_minimum_required(VERSION 3.22.1)
project("smt")

add_library(smt SHARED
        native-lib.cpp
        yin.cpp)

target_include_directories(smt PRIVATE
        .
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party/pocketfft)

target_compile_features(smt PRIVATE cxx_std_17)

find_library(log-lib log)
target_link_libraries(smt ${log-lib})
