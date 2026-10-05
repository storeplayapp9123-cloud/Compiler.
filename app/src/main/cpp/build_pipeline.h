#ifndef AALAM_BUILD_PIPELINE_H
#define AALAM_BUILD_PIPELINE_H

#include <string>
#include "aalam_compiler.h"

class BuildPipeline {
public:
    BuildPipeline(const std::string& projectDir, aalam_log_fn log, void* user);
    int run(const std::string& target);

private:
    void say(const std::string& msg);
    bool prepare(const std::string& target);
    bool compileJava();

    std::string dir_;
    aalam_log_fn log_;
    void* user_;
    std::string appName_;
};

#endif
