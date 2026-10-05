#include "build_pipeline.h"

#include <sys/stat.h>
#include <fstream>
#include <sstream>

namespace {

bool exists(const std::string& path) {
    struct stat st;
    return stat(path.c_str(), &st) == 0;
}

std::string readFile(const std::string& path) {
    std::ifstream f(path);
    std::stringstream ss;
    ss << f.rdbuf();
    return ss.str();
}

// Tiny helper: finds "key": "value" in a JSON string.
std::string jsonString(const std::string& json, const std::string& key) {
    size_t p = json.find("\"" + key + "\"");
    if (p == std::string::npos) return "";
    p = json.find(':', p);
    if (p == std::string::npos) return "";
    p = json.find('"', p);
    if (p == std::string::npos) return "";
    size_t e = json.find('"', p + 1);
    if (e == std::string::npos) return "";
    return json.substr(p + 1, e - p - 1);
}

}  // namespace

BuildPipeline::BuildPipeline(const std::string& projectDir, aalam_log_fn log,
                             aalam_step_fn step, void* user)
    : dir_(projectDir), log_(log), step_(step), user_(user) {}

void BuildPipeline::say(const std::string& msg) {
    if (log_) log_(msg.c_str(), user_);
}

int BuildPipeline::run(const std::string& target) {
    if (!prepare(target)) return 1;
    if (!compileResources()) return 2;
    if (!compileJava()) return 3;
    say("Steps 4-7 not implemented yet - next.");
    return 4;
}

bool BuildPipeline::prepare(const std::string& target) {
    say("[1/7] Preparing...");

    std::string manifestPath = dir_ + "/manifest.json";
    if (!exists(manifestPath)) {
        say("ERROR: manifest.json not found in .asc");
        return false;
    }

    std::string json = readFile(manifestPath);
    appName_ = jsonString(json, "name");
    if (appName_.empty()) {
        say("ERROR: manifest.json has no \"name\"");
        return false;
    }
    if (json.find("\"" + target + "\"") == std::string::npos) {
        say("ERROR: platform '" + target + "' not listed in manifest");
        return false;
    }
    if (!exists(dir_ + "/src")) {
        say("ERROR: src/ folder missing in .asc");
        return false;
    }

    say("Project: " + appName_);
    say("Target: " + target);
    say("[1/7] Preparing... OK");
    return true;
}

bool BuildPipeline::compileResources() {
    say("[2/7] Processing resources (aapt2)...");
    if (!step_) {
        say("ERROR: no Java step runner available");
        return false;
    }
    int rc = step_("aapt2", dir_.c_str(), user_);
    if (rc != 0) {
        say("[2/7] Processing resources... FAILED");
        return false;
    }
    say("[2/7] Processing resources... OK");
    return true;
}

bool BuildPipeline::compileJava() {
    say("[3/7] Compiling Java (ECJ)...");
    if (!step_) {
        say("ERROR: no Java step runner available");
        return false;
    }
    int rc = step_("ecj", dir_.c_str(), user_);
    if (rc != 0) {
        say("[3/7] Compiling Java... FAILED");
        return false;
    }
    say("[3/7] Compiling Java... OK");
    return true;
}
