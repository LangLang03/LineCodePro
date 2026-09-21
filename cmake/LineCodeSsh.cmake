include_guard(GLOBAL)

include(FetchContent)
if (POLICY CMP0135)
    cmake_policy(SET CMP0135 NEW)
endif ()

# libssh2 is the portable protocol engine used by LineCode's C++ SSH
# infrastructure.  Both dependencies are pinned to immutable upstream commits;
# no system SSH package is required by Android builds.
set(LINECODE_LIBSSH2_COMMIT
    "a312b43325e3383c865a87bb1d26cb52e3292641")
set(LINECODE_MBEDTLS_VERSION "3.6.4")

function(linecode_enable_ssh target_name)
    if (NOT TARGET ${target_name})
        message(FATAL_ERROR "linecode_enable_ssh target does not exist: ${target_name}")
    endif ()

    enable_language(C)

    # Android has no public NDK OpenSSL contract.  Build the crypto backend from
    # a pinned mbedTLS source revision there; desktop keeps the platform-native
    # backend selected below.
    if (ANDROID)
        set(ENABLE_PROGRAMS OFF CACHE BOOL "" FORCE)
        set(ENABLE_TESTING OFF CACHE BOOL "" FORCE)
        set(USE_STATIC_MBEDTLS_LIBRARY ON CACHE BOOL "" FORCE)
        set(USE_SHARED_MBEDTLS_LIBRARY OFF CACHE BOOL "" FORCE)
        set(DISABLE_PACKAGE_CONFIG_AND_INSTALL ON CACHE BOOL "" FORCE)
        FetchContent_Declare(linecode_mbedtls
            # The official release archive includes the framework submodule;
            # GitHub's source snapshot does not and cannot configure mbedTLS.
            URL "https://github.com/Mbed-TLS/mbedtls/releases/download/mbedtls-${LINECODE_MBEDTLS_VERSION}/mbedtls-${LINECODE_MBEDTLS_VERSION}.tar.bz2"
            URL_HASH "SHA256=ec35b18a6c593cf98c3e30db8b98ff93e8940a8c4e690e66b41dfc011d678110"
        )
        FetchContent_GetProperties(linecode_mbedtls)
        if (NOT linecode_mbedtls_POPULATED)
            FetchContent_Populate(linecode_mbedtls)
            add_subdirectory(
                "${linecode_mbedtls_SOURCE_DIR}"
                "${linecode_mbedtls_BINARY_DIR}"
                EXCLUDE_FROM_ALL
            )
        endif ()
        set(CRYPTO_BACKEND "mbedTLS" CACHE STRING "" FORCE)
        # FindMbedTLS from libssh2 is written for installed libraries and can
        # otherwise overwrite a cached MBEDTLS_FOUND value while probing
        # pkg-config.  Seed both its input variables and result variables in
        # this function scope so the already-created source targets are used.
        set(MBEDTLS_INCLUDE_DIR "${linecode_mbedtls_SOURCE_DIR}/include")
        set(MBEDCRYPTO_LIBRARY mbedcrypto)
        set(MBEDTLS_FOUND TRUE)
        set(MBEDTLS_INCLUDE_DIRS "${linecode_mbedtls_SOURCE_DIR}/include")
        # libssh2 unconditionally generates a build-tree export.  Point that
        # export at an imported facade so CMake does not require the source
        # mbedcrypto target to belong to libssh2's unrelated export set.
        if (NOT TARGET linecode_mbedcrypto)
            add_library(linecode_mbedcrypto STATIC IMPORTED GLOBAL)
            set_property(TARGET linecode_mbedcrypto PROPERTY IMPORTED_LOCATION
                "${linecode_mbedtls_BINARY_DIR}/library/${CMAKE_STATIC_LIBRARY_PREFIX}mbedcrypto${CMAKE_STATIC_LIBRARY_SUFFIX}")
            add_dependencies(linecode_mbedcrypto mbedcrypto)
        endif ()
        set(MBEDTLS_LIBRARIES linecode_mbedcrypto)
        set(MBEDTLS_VERSION "${LINECODE_MBEDTLS_VERSION}")
    elseif (WIN32)
        set(CRYPTO_BACKEND "WinCNG" CACHE STRING "" FORCE)
    else ()
        # libssh2 resolves the platform OpenSSL package and carries the exact
        # transitive target.  The protocol implementation itself remains pinned.
        set(CRYPTO_BACKEND "OpenSSL" CACHE STRING "" FORCE)
    endif ()

    set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
    set(BUILD_STATIC_LIBS ON CACHE BOOL "" FORCE)
    set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
    set(ENABLE_DEBUG_LOGGING OFF CACHE BOOL "" FORCE)
    set(ENABLE_ZLIB_COMPRESSION OFF CACHE BOOL "" FORCE)
    FetchContent_Declare(linecode_libssh2
        URL "https://github.com/libssh2/libssh2/archive/${LINECODE_LIBSSH2_COMMIT}.tar.gz"
        URL_HASH "SHA256=2501e79b036b7fa50ac1f43d0151398049770fc5696d12bc8e4e293e082c545d"
    )
    # MakeAvailable has been supported since CMake 3.14 and remains the
    # non-deprecated population path in CMake 4.x.  It also keeps the
    # dependency target idempotent when the app and protocol fixture share a
    # configure graph.
    FetchContent_MakeAvailable(linecode_libssh2)

    target_link_libraries(${target_name} PRIVATE libssh2_static)
endfunction()
