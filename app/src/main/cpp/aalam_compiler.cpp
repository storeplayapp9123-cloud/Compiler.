#include "aalam_compiler.h"
#include "build_pipeline.h"

int aalam_build(const char* project_dir, const char* target,
                aalam_log_fn log, aalam_step_fn step, void* user) {
    if (!project_dir || !target) return -1;
    BuildPipeline pipeline(project_dir, log, step, user);
    return pipeline.run(target);
}

const char* aalam_version(void) {
    return "0.2.0";
}
