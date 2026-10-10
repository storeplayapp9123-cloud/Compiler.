#include "build_pipeline.h"

#include <sys/stat.h>
#include <algorithm>
#include <cctype>
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

std::string lower(std::string s) {
    std::transform(s.begin(), s.end(), s.begin(),
                   [](unsigned char c) { return std::tolower(c); });
    return s;
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
    if (!runStep("[2/7] Processing resources (aapt2)", "aapt2")) return 2;
    if (!runStep("[3/7] Compiling Java (ECJ)", "ecj")) return 3;
    if (!runStep("[4/7] Optimizing to DEX (D8)", "d8")) return 4;
    if (!runStep("[5/7] Packaging APK", "pack")) return 5;
    if (!runStep("[6/7] Signing APK", "sign")) return 6;
    if (!runStep("[7/7] Finishing", "finish")) return 7;
    say("BUILD COMPLETE. Tap SAVE APK to export.");
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
    if (lower(json).find("\"" + lower(target) + "\"") == std::string::npos) {
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

// Runs one step that is implemented in the Java layer.
bool BuildPipeline::runStep(const std::string& label, const std::string& step) {
    say(label + "...");
    if (!step_) {
        say("ERROR: no Java step runner available");
        return false;
    }
    int rc = step_(step.c_str(), dir_.c_str(), user_);
    if (rc != 0) {
        say(label + "... FAILED");
        return false;
    }
    say(label + "... OK");
    return true;
}
