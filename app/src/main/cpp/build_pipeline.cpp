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

BuildPipeline::BuildPipeline(const std::string& projectDir,
                             aalam_log_fn log, void* user)
    : dir_(projectDir), log_(log), user_(user) {}

void BuildPipeline::say(const std::string& msg) {
    if (log_) log_(msg.c_str(), user_);
}

int BuildPipeline::run(const std::string& target) {
    if (!prepare(target)) return 1;
    if (!compileJava()) return 2;
    say("Build finished.");
    return 0;
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

bool BuildPipeline::compileJava() {
    say("[2/7] Compiling Java (ECJ)...");
    say("Not implemented yet - next step.");
    return false;
}
