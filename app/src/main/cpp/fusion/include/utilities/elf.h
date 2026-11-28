#ifndef FUSION_ELF_H
#define FUSION_ELF_H

#include <cstdint>

// Resolve a unique, defined function in matching ELF files. Reject missing or
// mismatched GNU build IDs, malformed symbols and targets outside executable code.
uintptr_t get_function_rva_from_files(const char* library_path, const char* symbol_path,
                                      const char* target_symbol);

#endif // FUSION_ELF_H
