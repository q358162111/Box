#coding=utf-8
#!/usr/bin/python
import os
import requests
from importlib.machinery import SourceFileLoader  ### 导入这个模块
from urllib import parse
import json
import sys
sys.dont_write_bytecode = True

def createFile(file_path):
    if os.path.exists(file_path) is False:
        os.makedirs(file_path)

def redirectResponse(tUrl):
  # 限制最大跳转次数并设置超时，避免循环重定向递归爆栈、网络挂起线程卡死
  currentUrl = tUrl
  for _ in range(10):
    rsp = requests.get(currentUrl, allow_redirects=False, verify=False, timeout=(5, 10))
    if 'Location' in rsp.headers:
      currentUrl = rsp.headers['Location']
    else:
      return rsp
  return rsp

def downloadFile(name,url):
    try:
        rsp = redirectResponse(url)
        # 先写临时文件，成功后再落盘，避免留下损坏的半成品插件
        tmpName = name + '.tmp'
        with open(tmpName,'wb') as f:
            f.write(rsp.content)
        os.replace(tmpName, name)
        print(url)
    except Exception as e:
        # 清理可能残留的临时文件
        try:
            if os.path.exists(name + '.tmp'):
                os.remove(name + '.tmp')
        except OSError:
            pass
        print(name + ' =======================================> error: ' + str(e))
        print(url)

def downloadPlugin(basePath,url):
    createFile(basePath)
    name = url.split('/')[-1].split('.')[0]
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
    name = fileName.split('/')[-1].split('.')[0]
    spList = gParam['SpiderList']
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
