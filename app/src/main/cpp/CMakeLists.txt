cmake_minimum_required(VERSION 3.22.1)
project(aalam)

set(CMAKE_CXX_STANDARD 17)

add_library(aalam SHARED
    native-lib.cpp
    aalam_compiler.cpp
    build_pipeline.cpp)

find_library(log-lib log)
target_link_libraries(aalam ${log-lib})
