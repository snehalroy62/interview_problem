cmake_minimum_required(VERSION 3.25)
project(challenge)

set(CMAKE_CXX_STANDARD 23)

include(FetchContent)
FetchContent_Declare(cpr GIT_REPOSITORY https://github.com/libcpr/cpr.git GIT_TAG b3755347973a23567e1f786615ebb480e1f704c4) # 1.11.3
FetchContent_MakeAvailable(cpr)
FetchContent_Declare(json URL https://github.com/nlohmann/json/releases/download/v3.12.0/json.tar.xz URL_HASH SHA256=42f6e95cad6ec532fd372391373363b62a14af6d771056dbfc86160e6dfff7aa)
FetchContent_MakeAvailable(json)
FetchContent_Declare(argparse GIT_REPOSITORY https://github.com/morrisfranken/argparse GIT_TAG be309f0b0199b832cbead3a93aeee287c3e6bcfb)
FetchContent_MakeAvailable(argparse)

if(EXISTS "src/CMakeLists.txt") # Allows for incremental builds in Dockerfile
    add_subdirectory(src)
endif()
