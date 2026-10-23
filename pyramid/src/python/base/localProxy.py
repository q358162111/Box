# 本地代理端口：默认 9978（与 RemoteServer 默认端口一致），
# 运行时可由 Java 侧经 base.setProxyPort 注入真实端口，避免硬编码
_proxyPort = 9978

def setProxyPort(port):
    global _proxyPort
    try:
        p = int(port)
        if p > 0:
            _proxyPort = p
    except (TypeError, ValueError):
        pass

class Proxy:
    def getUrl(self, local):
        return 'http://127.0.0.1:' + str(_proxyPort)

    def getPort(self):
        return _proxyPort
