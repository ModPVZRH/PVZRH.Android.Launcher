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

option(FUSION_BUILD_TESTS "Build the Android ARM64 native hook regression executables" OFF)
if(FUSION_BUILD_TESTS)
    add_executable(elf_symbol_test tests/elf_symbol_test.cpp fusion/src/utilities/elf.cpp)
    target_include_directories(elf_symbol_test PRIVATE fusion/include)
    target_compile_features(elf_symbol_test PRIVATE cxx_std_17)
    target_compile_options(elf_symbol_test PRIVATE -UNDEBUG)
    target_link_options(elf_symbol_test PRIVATE -static-libstdc++)
    add_library(unity_symbol_fixture SHARED tests/unity_symbol_fixture.cpp)
    target_link_options(unity_symbol_fixture PRIVATE -static-libstdc++)
    add_library(unity_symbol_fixture_no_path SHARED tests/unity_symbol_fixture.cpp)
    target_link_options(unity_symbol_fixture_no_path PRIVATE -static-libstdc++)
    target_compile_definitions(unity_symbol_fixture_no_path PRIVATE OMIT_PATH_CHECK)
    add_executable(libunity_hook_test tests/libunity_hook_test.cpp
        fusion/src/hooking/libunity.cpp fusion/src/hooking/safehook.cpp fusion/src/utilities/elf.cpp)
    target_include_directories(libunity_hook_test PRIVATE fusion/include ${CMAKE_SOURCE_DIR})
    target_compile_features(libunity_hook_test PRIVATE cxx_std_17)
    target_compile_options(libunity_hook_test PRIVATE -UNDEBUG)
    target_link_options(libunity_hook_test PRIVATE -static-libstdc++)
    target_link_libraries(libunity_hook_test PRIVATE android log
        ${CMAKE_SOURCE_DIR}/../jniLibs/${CMAKE_ANDROID_ARCH_ABI}/libdobby.so)
    add_executable(return_buffer_test tests/return_buffer_test.cpp)
    target_compile_options(return_buffer_test PRIVATE -UNDEBUG)
    target_link_libraries(return_buffer_test PRIVATE fusion)
    add_executable(short_hook_test tests/short_hook_test.cpp tests/short_functions.S)
    target_compile_options(short_hook_test PRIVATE -UNDEBUG)
    target_link_libraries(short_hook_test PRIVATE fusion)
    add_executable(chained_hook_test tests/chained_hook_test.cpp tests/chained_functions.S)
    target_compile_options(chained_hook_test PRIVATE -UNDEBUG)
    target_link_options(chained_hook_test PRIVATE -Wl,--no-relax)
    target_link_libraries(chained_hook_test PRIVATE fusion)
    add_executable(crowded_hook_test tests/crowded_hook_test.cpp)
    target_compile_options(crowded_hook_test PRIVATE -UNDEBUG)
    target_link_libraries(crowded_hook_test PRIVATE fusion)
endif()
