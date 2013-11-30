import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

import java.io.File
import java.net.{JarURLConnection, URL}
import java.util.jar.{JarEntry, JarFile}

object ClassFinder {
  def main(args: Array[String]): Unit =
    new ClassFinder().findClasses(args(0)).foreach(println)
}

class ClassFinder(private val classLoader: ClassLoader) {
  def this() = this(Thread.currentThread.getContextClassLoader)

  private def pathToClassName(path: String): String = path.substring(0, path.size - ".class".size)

  private def isClassFile(entry: JarEntry): Boolean = isClassFile(entry.getName)
  private def isClassFile(file: File): Boolean = file.isFile && isClassFile(file.getName)
  private def isClassFile(filePath: String): Boolean = filePath.endsWith(".class")

  private def resourceNameToClassName(resourceName: String): String =
    pathToClassName(resourceNameToPackageName(resourceName))
  private def resourceNameToPackageName(resourceName: String): String =
    resourceName.replace('/', '.')
  private def packageNameToResourceName(packageName: String): String =
    packageName.replace('.', '/')

  private val finderFunction: PartialFunction[URL, String => List[Class[_]]] =
    findClassesWithFile orElse
    findClassesWithJarFile orElse
    findClassesWithNone

  def findClasses(rootPackageName: String): List[Class[_]] = {
    val resourceName = packageNameToResourceName(rootPackageName)
    classLoader.getResource(resourceName) match {
      case null => Nil
      case url => finderFunction(url)(rootPackageName)
    }
  }

  def findClassesWithFile: PartialFunction[URL, String => List[Class[_]]] = {
    case url if url.getProtocol == "file" =>
      val classes = new ListBuffer[Class[_]]

      def findClassesWithFileInner(packageName: String, dir: File): List[Class[_]] = {
        dir.list.foreach { path =>
          new File(dir, path) match {
            case file if isClassFile(file) =>
              classes += classLoader.loadClass(packageName + "." + pathToClassName(file.getName))
            case directory if directory.isDirectory =>
              findClassesWithFileInner(packageName + "." + directory.getName, directory)
            case _ =>
          }
        }

        classes toList
      }

      findClassesWithFileInner(_: String, new File(url.getFile))
  }

  def findClassesWithJarFile: PartialFunction[URL, String => List[Class[_]]] = {
    case url if url.getProtocol == "jar" =>
      def manageJar[T](jarFile: JarFile)(body: JarFile => T): T = try {
        body(jarFile)
      } finally {
        jarFile.close()
      }

      def findClassesWithJarFileInner(packageName: String): List[Class[_]] =
        url openConnection match {
          case jarURLConnection: JarURLConnection =>
            manageJar(jarURLConnection.getJarFile) { jarFile =>
              jarFile.entries.asScala.toList.collect {
                case jarEntry if resourceNameToPackageName(jarEntry.getName).startsWith(packageName) &&
                                  isClassFile(jarEntry) =>
                                    classLoader.loadClass(resourceNameToClassName(jarEntry.getName))
              }
            }
        }

      findClassesWithJarFileInner
  }

  def findClassesWithNone: PartialFunction[URL, String => List[Class[_]]] = {
    case _ => packageName => Nil
  }
}
