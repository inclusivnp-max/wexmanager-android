cmake_minimum_required(VERSION 3.22.1)
project(wexpty C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

add_library(wexpty SHARED wexpty.c)

target_compile_options(wexpty PRIVATE -Wall -Wextra)
