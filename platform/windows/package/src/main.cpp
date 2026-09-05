#include <windows.h>

#include <huxerui/windows/installer.h>

int WINAPI wWinMain(HINSTANCE, HINSTANCE, wchar_t*, int) {
  return huxerui::windows::RunInstallerApplication();
}
