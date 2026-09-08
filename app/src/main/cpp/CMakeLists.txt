cmake_minimum_required(VERSION 3.22.1)
project(aetherlauncher)

add_library(aetherlauncher SHARED native_launcher.cpp)

find_library(log-lib log)

target_link_libraries(aetherlauncher ${log-lib} dl)
