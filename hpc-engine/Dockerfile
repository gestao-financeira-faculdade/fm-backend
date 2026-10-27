FROM ubuntu:24.04 AS builder

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    cmake \
    pkg-config \
    libgrpc++-dev \
    libprotobuf-dev \
    protobuf-compiler \
    protobuf-compiler-grpc \
    ocl-icd-opencl-dev \
    opencl-headers \
    pocl-opencl-icd \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
COPY src/main/proto/risk_simulation.proto /workspace/src/main/proto/risk_simulation.proto
COPY hpc-engine/ /workspace/hpc-engine/

WORKDIR /workspace/hpc-engine/build
RUN cmake .. \
    -DCMAKE_BUILD_TYPE=Release \
    -DENABLE_OPENCL=ON \
    -DBUILD_GRPC_SERVER=ON \
    -DBUILD_TESTING=ON \
    && cmake --build . -j"$(nproc)" \
    && ctest --output-on-failure

FROM ubuntu:24.04 AS runtime

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
    libgrpc++1.51t64 \
    libprotobuf32t64 \
    ocl-icd-libopencl1 \
    pocl-opencl-icd \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /workspace/hpc-engine/build/hpc_grpc_server /app/hpc_grpc_server
COPY --from=builder /workspace/hpc-engine/kernels/monte_carlo.cl /app/kernels/monte_carlo.cl

EXPOSE 50051

ENTRYPOINT ["/app/hpc_grpc_server"]
