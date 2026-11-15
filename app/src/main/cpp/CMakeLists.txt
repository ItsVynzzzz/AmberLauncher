# Jembatan JNI ke Discord Social SDK untuk build 64-bit (hanya dibangun bila app/libs/discord_partner_sdk.aar
# ada; lihat app/build.gradle.kts). Build 32-bit memakai amber_discord.mk lewat ndk-build.
cmake_minimum_required(VERSION 3.18)
project(amber_discord LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Paket "prefab" ini datang dari discord_partner_sdk.aar (header + libdiscord_partner_sdk.so).
find_package(discord_partner_sdk REQUIRED CONFIG)

add_library(amber_discord SHARED discord_bridge.cpp)
target_link_libraries(amber_discord PRIVATE discord_partner_sdk::discord_partner_sdk log)
