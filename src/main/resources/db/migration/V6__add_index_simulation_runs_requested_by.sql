-- ============================================================================
-- V6: Add explicit B-Tree index for simulation_runs foreign key (requested_by)
-- Ensures compliance with MIG-03 (every FOREIGN KEY has an explicit index)
-- ============================================================================

CREATE INDEX idx_simulation_runs_requested_by ON simulation_runs (requested_by);
