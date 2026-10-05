#ifndef AALAM_COMPILER_H
#define AALAM_COMPILER_H

#ifdef __cplusplus
extern "C" {
#endif

typedef void (*aalam_log_fn)(const char* message, void* user);

/* Runs a step implemented outside C++ (e.g. "ecj"). Returns 0 on success. */
typedef int (*aalam_step_fn)(const char* step, const char* project_dir, void* user);

/* Returns 0 on success, non-zero on failure. */
int aalam_build(const char* project_dir, const char* target,
                aalam_log_fn log, aalam_step_fn step, void* user);

const char* aalam_version(void);

#ifdef __cplusplus
}
#endif

#endif
