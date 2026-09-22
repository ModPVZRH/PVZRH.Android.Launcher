cmake_minimum_required(VERSION 3.22.1)
project("bepinex_android" CXX ASM)

# FusionCore main branch architecture:
#   libmain.so  — registers NativeLoader load/unload, resolves fusion dynamically
#   libfusion.so — il2cpp_init hook, CoreCLR bootstrap, BepInEx entry
#
# These are NOT linked together. libmain resolves libfusion symbols
# at runtime via dlopen("libfusion.so", RTLD_NOLOAD) + dlsym.

add_subdirectory(main)
add_subdirectory(fusion)

option(FUSION_BUILD_TESTS "Build the Android ARM64 return-buffer regression executable" OFF)
if(FUSION_BUILD_TESTS)
    add_executable(return_buffer_test tests/return_buffer_test.cpp)
    target_compile_options(return_buffer_test PRIVATE -UNDEBUG)
    target_link_libraries(return_buffer_test PRIVATE fusion)
endif()
