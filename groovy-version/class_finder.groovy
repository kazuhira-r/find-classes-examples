// 引数にスキャン対象のパッケージ名
def rootPackageName = args[0]

def classLoader = Thread.currentThread().contextClassLoader
def resourceName = rootPackageName.replace('.', '/')
def loadedClasses = []

def url = classLoader.getResource(resourceName)

if (!url) {
    // 見つからなかったら、そこで終了
    println("Not Found ${rootPackageName} Package.")
    System.exit(0)
}

def traverseDir
traverseDir = { packageName, f ->
    if (f.directory) {
        // ディレクトリの場合は、パッケージ名に .ディレクトリ名 を足して再帰
        f.eachFile(traverseDir.curry("${packageName}.${f.name}"))
    } else if (f.file && f.name.endsWith(".class")) {
        loadedClasses << classLoader.loadClass("${packageName}.${f.name.replaceAll(/\.class$/, '')}")
    }
}

def findClassesWithJar = { jarFile ->
    try {
        for (jarEntry in jarFile.entries()) {
            if (jarEntry.name.startsWith(resourceName) &&
                    jarEntry.name.endsWith(".class")) {
                def className = jarEntry.name.replace('/', '.').replaceAll(/\.class$/, '')
                loadedClasses << classLoader.loadClass(className)
            }
        }
    } finally {
        if (jarFile) {
            jarFile.close()
        }
    }
}

switch (url.protocol) {
    case 'file':
        // パッケージ名を与えて、traverseDirクロージャに部分適用
        new File(url.file).eachFile(traverseDir.curry(rootPackageName))
        break
    case 'jar':
        findClassesWithJar(url.openConnection().jarFile)
        break
    default:
        break
}

loadedClasses.each {
    println("${it.name} extends [${findSuperClasses(it).collect{ it.name }.join(', ')}]")
}

// Utility Methods...
def findSuperClasses(targetClass) {
    def superclasses = []

    def findSuperClassesInner = { clazz ->
        if (!clazz) {
            return
        }

        def sc = clazz.superclass
        // java.lang.Objectは対象外
        if (sc && sc != Object) {
            superclasses << sc
            call(sc.superclass)
        }

        clazz.interfaces.each { ifc ->
            superclasses << ifc
            ifc.interfaces.each { owner.call(it) }
        }
    }

    findSuperClassesInner(targetClass)

    superclasses
}
