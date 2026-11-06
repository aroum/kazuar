# SPDX-License-Identifier: GPL-3.0-only
cmake_minimum_required(VERSION 3.22.1)
project(kazuar_native_regressions LANGUAGES CXX)
find_package(GTest REQUIRED)
find_package(JNI REQUIRED)
# Reuse the authoritative production list; Android JNI entry points are excluded on the host.
add_subdirectory(.. native-source-list EXCLUDE_FROM_ALL)
get_target_property(production_sources jni_latinime SOURCES)
list(FILTER production_sources INCLUDE REGEX "^src/")
list(TRANSFORM production_sources PREPEND "${CMAKE_CURRENT_SOURCE_DIR}/../")
add_library(native_regression_core STATIC ${production_sources})
target_include_directories(native_regression_core PUBLIC ../src ${JNI_INCLUDE_DIRS})
target_compile_features(native_regression_core PRIVATE cxx_std_11)
target_compile_options(native_regression_core PRIVATE -ffunction-sections -fdata-sections
    -fsanitize=address,undefined,float-cast-overflow -fno-omit-frame-pointer)
enable_testing()
add_executable(native_regressions
    suggest/core/suggestion_safety_regression_test.cpp
    suggest/core/dicnode/dic_node_pool_test.cpp
    suggest/core/layout/normal_distribution_2d_test.cpp
    dictionary/utils/sparse_table_test.cpp
    dictionary/utils/probability_utils_test.cpp
    dictionary/utils/format_utils_test.cpp
    dictionary/utils/byte_array_utils_test.cpp
    dictionary/utils/buffer_with_extendable_buffer_test.cpp
    dictionary/utils/bloom_filter_test.cpp
    dictionary/structure/v4/content/language_model_dict_content_test.cpp
    dictionary/structure/v4/content/language_model_dict_content_global_counters_test.cpp
    dictionary/structure/v4/content/probability_entry_test.cpp
    dictionary/structure/v4/content/terminal_position_lookup_table_test.cpp
    dictionary/structure/pt_common/storage_safety_regression_test.cpp
    dictionary/structure/pt_common/dictionary_file_regression_test.cpp
    dictionary/utils/trie_map_test.cpp
    suggest/core/layout/geometry_utils_test.cpp
    dictionary/structure/pt_common/bigram/bigram_list_read_write_utils_test.cpp
    dictionary/structure/pt_common/native_reading_regression_test.cpp
    dictionary/structure/pt_common/dynamic_gc_regression_test.cpp)
target_include_directories(native_regressions PRIVATE ../src)
target_compile_features(native_regressions PRIVATE cxx_std_11)
# Unused dictionary file-writing entry points are not part of these in-memory tests.
target_compile_options(native_regressions PRIVATE -ffunction-sections -fdata-sections
    -fsanitize=address,undefined,float-cast-overflow -fno-omit-frame-pointer)
target_link_options(native_regressions PRIVATE -Wl,--gc-sections -fsanitize=address,undefined,float-cast-overflow)
target_link_libraries(native_regressions PRIVATE GTest::gtest_main native_regression_core dl)
include(GoogleTest)
gtest_discover_tests(native_regressions PROPERTIES TIMEOUT 60)
