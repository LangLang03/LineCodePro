// IBaseIpcService.aidl
package cn.lineai.ipc;

interface IBaseIpcService {
    String getProviderType();
    String getProviderInfo();
    boolean isAvailable();
}
