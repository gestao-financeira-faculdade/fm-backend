cmake_minimum_required(VERSION 3.22)
project(gestao_financeira_hpc_engine VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(ENABLE_OPENCL "Enable OpenCL GPU/CPU kernel acceleration" OFF)
option(BUILD_GRPC_SERVER "Build gRPC server binary (requires Protobuf and gRPC)" ON)
option(BUILD_TESTING "Build C++20 unit tests for simulation engine" ON)

find_package(Threads REQUIRED)

add_library(simulation_core STATIC
    src/simulation_engine.cpp
)

target_include_directories(simulation_core PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/src
)

target_link_libraries(simulation_core PUBLIC Threads::Threads)

if(ENABLE_OPENCL)
    find_package(OpenCL)
    if(OpenCL_FOUND)
        target_compile_definitions(simulation_core PUBLIC ENABLE_OPENCL=1)
        target_link_libraries(simulation_core PUBLIC OpenCL::OpenCL)
    endif()
endif()

if(BUILD_GRPC_SERVER)
    find_package(Protobuf QUIET)
    find_package(gRPC CONFIG QUIET)

    if(Protobuf_FOUND AND gRPC_FOUND)
        set(PROTO_FILE "${CMAKE_CURRENT_SOURCE_DIR}/../src/main/proto/risk_simulation.proto")
        set(GENERATED_PROTO_DIR "${CMAKE_CURRENT_BINARY_DIR}/generated")
        file(MAKE_DIRECTORY ${GENERATED_PROTO_DIR})

        set(PROTO_SRC "${GENERATED_PROTO_DIR}/risk_simulation.pb.cc")
        set(PROTO_HDR "${GENERATED_PROTO_DIR}/risk_simulation.pb.h")
        set(GRPC_SRC "${GENERATED_PROTO_DIR}/risk_simulation.grpc.pb.cc")
        set(GRPC_HDR "${GENERATED_PROTO_DIR}/risk_simulation.grpc.pb.h")

        add_custom_command(
            OUTPUT "${PROTO_SRC}" "${PROTO_HDR}" "${GRPC_SRC}" "${GRPC_HDR}"
            COMMAND $<TARGET_FILE:protobuf::protoc>
            ARGS --grpc_out="${GENERATED_PROTO_DIR}"
                 --cpp_out="${GENERATED_PROTO_DIR}"
                 -I "${CMAKE_CURRENT_SOURCE_DIR}/../src/main/proto"
                 --plugin=protoc-gen-grpc=$<TARGET_FILE:gRPC::grpc_cpp_plugin>
                 "${PROTO_FILE}"
            DEPENDS "${PROTO_FILE}"
            COMMENT "Generating C++ Protobuf and gRPC sources from risk_simulation.proto"
        )

        add_executable(hpc_grpc_server
            src/grpc_server.cpp
            ${PROTO_SRC}
            ${GRPC_SRC}
        )

        target_include_directories(hpc_grpc_server PRIVATE
            ${GENERATED_PROTO_DIR}
            ${CMAKE_CURRENT_SOURCE_DIR}/src
        )

        target_link_libraries(hpc_grpc_server PRIVATE
            simulation_core
            gRPC::grpc++
            protobuf::libprotobuf
        )
    else()
        message(STATUS "Protobuf/gRPC not found in local environment; skipping hpc_grpc_server target")
    endif()
endif()

if(BUILD_TESTING)
    enable_testing()
    add_executable(simulation_engine_test
        tests/simulation_engine_test.cpp
    )
    target_link_libraries(simulation_engine_test PRIVATE simulation_core)
    add_test(NAME simulation_engine_test COMMAND simulation_engine_test)
endif()
