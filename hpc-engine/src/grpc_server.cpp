#include "simulation_engine.hpp"
#include "risk_simulation.grpc.pb.h"

#include <grpcpp/grpcpp.h>
#include <cstdlib>
#include <iostream>
#include <memory>
#include <string>

namespace gestaofinanceira::hpc {

using com::emanudev::gestaofinanceira::simulation::grpc::DailyProjectionBand;
using com::emanudev::gestaofinanceira::simulation::grpc::RiskSimulationRequest;
using com::emanudev::gestaofinanceira::simulation::grpc::RiskSimulationResponse;
using com::emanudev::gestaofinanceira::simulation::grpc::RiskSimulationService;

class RiskSimulationServiceImpl final : public RiskSimulationService::Service {
public:
    explicit RiskSimulationServiceImpl(SimulationEngine engine)
        : engine_(std::move(engine)) {}

    grpc::Status SimulateCashFlowRisk(
        grpc::ServerContext* /*context*/,
        const RiskSimulationRequest* request,
        RiskSimulationResponse* response
    ) override {
        try {
            SimulationParams params;
            params.initial_balance_cents = request->initial_balance_cents();
            params.horizon_days = request->horizon_days();
            params.num_scenarios = request->num_scenarios();
            params.expected_default_rate = request->expected_default_rate();
            params.variable_cost_shock_pct = request->variable_cost_shock_pct();
            params.random_seed = request->random_seed();
            params.scheduled_items.reserve(static_cast<size_t>(request->scheduled_items_size()));

            for (const auto& proto_item : request->scheduled_items()) {
                params.scheduled_items.push_back(ScheduledItem{
                    .day_offset = proto_item.day_offset(),
                    .payable_fixed_cents = proto_item.payable_fixed_cents(),
                    .payable_variable_cents = proto_item.payable_variable_cents(),
                    .receivable_cents = proto_item.receivable_cents()
                });
            }

            const SimulationOutput output = engine_.simulate(params);

            response->set_var_99_cents(output.var_99_cents);
            response->set_median_p50_cents(output.median_p50_cents);
            response->set_ruin_probability_pct(output.ruin_probability_pct);
            response->set_execution_device(output.execution_device);
            response->set_execution_time_ms(output.execution_time_ms);

            for (const auto& band : output.daily_bands) {
                DailyProjectionBand* proto_band = response->add_daily_bands();
                proto_band->set_day_offset(band.day_offset);
                proto_band->set_p01_cents(band.p01_cents);
                proto_band->set_p05_cents(band.p05_cents);
                proto_band->set_p25_cents(band.p25_cents);
                proto_band->set_p50_cents(band.p50_cents);
                proto_band->set_p75_cents(band.p75_cents);
                proto_band->set_p95_cents(band.p95_cents);
            }

            return grpc::Status::OK;
        } catch (const std::invalid_argument& ex) {
            return grpc::Status(grpc::StatusCode::INVALID_ARGUMENT, ex.what());
        } catch (const std::exception& ex) {
            return grpc::Status(grpc::StatusCode::INTERNAL, ex.what());
        }
    }

private:
    SimulationEngine engine_;
};

} // namespace gestaofinanceira::hpc

int main(int argc, char** argv) {
    (void)argc;
    (void)argv;

    const char* port_env = std::getenv("HPC_GRPC_PORT");
    const std::string server_address = std::string("0.0.0.0:") + (port_env != nullptr ? port_env : "50051");

    gestaofinanceira::hpc::SimulationEngine engine(true);
    gestaofinanceira::hpc::RiskSimulationServiceImpl service(std::move(engine));

    grpc::ServerBuilder builder;
    builder.AddListeningPort(server_address, grpc::InsecureServerCredentials());
    builder.RegisterService(&service);

    std::unique_ptr<grpc::Server> server(builder.BuildAndStart());
    std::cout << "[HPC-Engine] RiskSimulationService listening on " << server_address << std::endl;
    server->Wait();
    return 0;
}
