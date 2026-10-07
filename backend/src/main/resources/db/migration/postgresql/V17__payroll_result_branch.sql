-- FR-087/FR-006: snapshot the branch so visibility of finalized results never depends on later employee changes.
ALTER TABLE payroll_result ADD COLUMN branch_id UUID;
