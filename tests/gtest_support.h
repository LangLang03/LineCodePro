#pragma once

#include <gtest/gtest.h>

// GoogleTest's EXPECT_TRUE macro accepts one preprocessor argument. Existing
// C++ contract expressions frequently contain braced initializer lists whose
// commas are not protected at preprocessing time. The extra parentheses make
// the complete variadic expression one argument without changing its value.
#define EXPECT_EXPRESSION(...) EXPECT_TRUE((__VA_ARGS__))
