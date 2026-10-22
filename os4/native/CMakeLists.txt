cmake_minimum_required(VERSION 3.22.1)
project(liquiddock4 C)

add_library(liquiddock4 SHARED
    native_entry.c
    process_gate.c
)

target_compile_features(liquiddock4 PRIVATE c_std_11)
target_compile_options(liquiddock4 PRIVATE
    -D_GNU_SOURCE
    -fvisibility=hidden
    -Wall
    -Wextra
    -Werror
)
target_link_libraries(liquiddock4 PRIVATE log dl)
set_target_properties(liquiddock4 PROPERTIES
    C_VISIBILITY_PRESET hidden
    POSITION_INDEPENDENT_CODE ON
)
