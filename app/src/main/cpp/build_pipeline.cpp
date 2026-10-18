#include "build_pipeline.h"

#include <dirent.h>
#include <sys/stat.h>
#include <unistd.h>
#include <cstdio>
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

// base ke andar (max depth tak) wo folder dhundho jisme AndroidManifest.xml ho
bool findManifestDir(const std::string& base, int depth, std::string& out) {
    if (exists(base + "/AndroidManifest.xml")) {
        out = base;
        return true;
    }
    if (depth == 0) return false;
    DIR* d = opendir(base.c_str());
    if (!d) return false;
    bool found = false;
    while (dirent* e = readdir(d)) {
        std::string n = e->d_name;
        if (n == "." || n == "..") continue;
        std::string sub = base + "/" + n;
        struct stat st;
        if (stat(sub.c_str(), &st) == 0 && S_ISDIR(st.st_mode) &&
            findManifestDir(sub, depth - 1, out)) {
            found = true;
            break;
        }
    }
    closedir(d);
    return found;
}

// <manifest> me package= nahi hai to inject karo
void ensurePackage(const std::string& manifestPath, const std::string& pkg) {
    std::string xml = readFile(manifestPath);
    if (xml.empty() || xml.find("package=") != std::string::npos) return;
    size_t p = xml.find("<manifest");
    if (p == std::string::npos) return;
    xml.insert(p + 9, " package=\"" + pkg + "\"");
    std::ofstream f(manifestPath, std::ios::trunc);
    f << xml;
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

    // App project ka AndroidManifest.xml dhundo
    // (Studio: src/Android/app/src/main/, purana flat: root)
    std::string appRoot;
    if (!findManifestDir(dir_, 6, appRoot)) {
        say("ERROR: AndroidManifest.xml missing in .asc");
        say("Searched in: " + dir_);
        return false;
    }

    if (appRoot != dir_) {
        // Studio structure: src/ ke andar poora project hai. Use _project me
        // hata do, taaki root pe purana flat layout ban sake.
        std::string srcDir = dir_ + "/src";
        std::string moved = dir_ + "/_project";
        if (appRoot.compare(0, srcDir.size() + 1, srcDir + "/") == 0 && !exists(moved)) {
            if (rename(srcDir.c_str(), moved.c_str()) == 0) {
                appRoot = moved + appRoot.substr(srcDir.size());
            }
        }
        say("App root: " + appRoot);

        if (!exists(dir_ + "/AndroidManifest.xml"))
            symlink((appRoot + "/AndroidManifest.xml").c_str(),
                    (dir_ + "/AndroidManifest.xml").c_str());
        if (!exists(dir_ + "/res") && exists(appRoot + "/res"))
            symlink((appRoot + "/res").c_str(), (dir_ + "/res").c_str());
        if (!exists(dir_ + "/src") && exists(appRoot + "/java"))
            symlink((appRoot + "/java").c_str(), (dir_ + "/src").c_str());
    }

    // Safety net: package= na ho to manifest.json se (ya default) daal do
    std::string pkg = jsonString(json, "package");
    if (pkg.empty()) pkg = "com.aalamstudio.app";
    ensurePackage(dir_ + "/AndroidManifest.xml", pkg);

    if (!exists(dir_ + "/src")) {
        say("ERROR: no src/ or java/ folder in .asc");
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
