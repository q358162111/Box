#coding=utf-8
#!/usr/bin/python
import os
import requests
import hashlib
from importlib.machinery import SourceFileLoader  ### 导入这个模块
from urllib import parse
import json
import sys
sys.dont_write_bytecode = True
from base.localProxy import setProxyPort

def setLocalProxyPort(port):
    # Java 侧注入真实本地服务端口，替代 localProxy.py 的硬编码 9978
    setProxyPort(port)
    return True

def createFile(file_path):
    if os.path.exists(file_path) is False:
        os.makedirs(file_path)

def redirectResponse(tUrl):
  # 限制最大跳转次数并设置超时，避免循环重定向递归爆栈、网络挂起线程卡死
  # 严格校验 HTTPS 证书：证书异常即视为下载失败（不做 verify=False 回退），
  # 防止中间人投毒插件代码；插件源需使用有效证书
  currentUrl = tUrl
  for _ in range(10):
    rsp = requests.get(currentUrl, allow_redirects=False, timeout=(5, 10))
    if 'Location' in rsp.headers:
      location = rsp.headers['Location']
      rsp.close()
      if location.startswith(('http://', 'https://')):
        currentUrl = location
      else:
        currentUrl = parse.urljoin(currentUrl, location)
    else:
      return rsp
  return rsp

def downloadFile(name,url):
    try:
        rsp = redirectResponse(url)
        try:
            # 非 200 不写盘：404/500/登录页等错误响应不得落盘为插件，
            # 否则会损坏缓存导致该插件反复加载失败、永久失效
            if rsp.status_code != 200:
                print(name + ' =======================================> error: HTTP ' + str(rsp.status_code))
                print(url)
                return False
            # 先写临时文件，成功后再落盘，避免留下损坏的半成品插件
            tmpName = name + '.tmp'
            with open(tmpName,'wb') as f:
                f.write(rsp.content)
            os.replace(tmpName, name)
            print(url)
            return True
        finally:
            rsp.close()
    except Exception as e:
        # 清理可能残留的临时文件；下载失败时删除已有的旧文件，
        # 避免下次 init 继续加载上次失败留下的损坏/过期插件
        try:
            if os.path.exists(name + '.tmp'):
                os.remove(name + '.tmp')
            if os.path.exists(name):
                os.remove(name)
        except OSError:
            pass
        print(name + ' =======================================> error: ' + str(e))
        print(url)
        return False

def pluginName(url):
    # 取 URL 的 path 再取 basename：避免无扩展名且带 query 的 URL 生成含 '?' 的非法/错误文件名（P-L12）
    path = parse.urlparse(url).path
    base = os.path.basename(path)
    if '.' in base:
        base = base.split('.')[0]
    if base == '':
        base = 'plugin'
    # 全量 URL 的短哈希：不同源的相同 basename 插件（如 a.com/xigua.py 与 b.com/xigua.py）
    # 不再互相覆盖缓存文件（P-M9），与 JS 端以 MD5(url) 命名保持一致
    digest = hashlib.md5(url.encode('utf-8')).hexdigest()[:8]
    return base + '_' + digest

def downloadPlugin(basePath,url):
    createFile(basePath)
    name = pluginName(url)
    pyName = ''
    if url.startswith('file://'):
        pyName = url.replace('file://','')
    else:
        pyName = basePath + name+'.py'
        downloadFile(pyName,url)
    sPath = gParam['SpiderPath']
    sPath[name] = pyName
    sParam = gParam['SpiderParam']
    paramList = parse.parse_qs(parse.urlparse(url).query).get('extend')
    if paramList == None:
        paramList = ['']
    sParam[name] = paramList[0]
    return pyName

def loadFromDisk(fileName):
    spList = gParam['SpiderList']
    sPath = gParam['SpiderPath']
    # 优先按已注册路径反查唯一逻辑名：文件名现在带哈希，直接 split 出的名可能与注册名不一致
    name = None
    for regName, regPath in sPath.items():
        if regPath == fileName:
            name = regName
            break
    if name is None:
        name = fileName.split('/')[-1].split('.')[0]
    if name not in spList:
        sp = SourceFileLoader(name, fileName).load_module().Spider()
        spList[name] = sp
    return spList[name]

def str2json(content):
    return json.loads(content)

gParam = {
    "SpiderList":{},
    "SpiderPath":{},
    "SpiderParam":{}
}

def getDependence(ru):
    result = ru.getDependence()
    return result

def getName(ru):
    result = ru.getName()
    return result

def init(ru,extend):
    spoList = []
    spList = gParam['SpiderList']
    sPath = gParam['SpiderPath']
    sParam = gParam['SpiderParam']
    for key in ru.getDependence():
        sp = None
        if key in spList.keys():
            sp = spList[key]
        elif key in sPath.keys():
            sp = loadFromDisk(sPath[key])
        if sp != None:
            sp.setExtendInfo(sParam[key])
            spoList.append(sp)
    ru.init(extend)

def homeContent(ru,filter):
    result = ru.homeContent(filter)
    formatJo = json.dumps(result,ensure_ascii=False)
    return formatJo

def homeVideoContent(ru):
    result = ru.homeVideoContent()
    formatJo = json.dumps(result,ensure_ascii=False)
    return formatJo

def categoryContent(ru,tid, pg, filter, extend):
    result = ru.categoryContent(tid, pg, filter, str2json(extend))
    formatJo = json.dumps(result,ensure_ascii=False)
    return formatJo

def detailContent(ru,array):
    result = ru.detailContent(str2json(array))
    formatJo = json.dumps(result,ensure_ascii=False)
    return formatJo

def playerContent(ru,flag,id,vipFlags):
    result = ru.playerContent(flag,id,str2json(vipFlags))
    formatJo = json.dumps(result,ensure_ascii=False)
    return formatJo

def liveContent(ru,url):
    result = ru.liveContent(url)
    return result

def searchContent(ru,key,quick):
    result = ru.searchContent(key,quick)
    formatJo = json.dumps(result,ensure_ascii=False)
    return formatJo

def localProxy(ru,param):
    result = ru.localProxy(str2json(param))
    return result

def run():
    pass

if __name__ == '__main__':
    run()
