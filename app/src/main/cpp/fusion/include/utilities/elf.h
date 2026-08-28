#ifndef FUSION_ELF_H
#define FUSION_ELF_H

#include <unistd.h>

uintptr_t get_rva_from_sym_file(const char* filepath, const char* target_symbol);

#endif // FUSION_ELF_H
