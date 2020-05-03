package org.nullvector

import org.nullvector.tree.Tree
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TreeSpec extends AnyFlatSpec with Matchers {

  it should """ has a root element """ in {
    Tree("Hola").toList shouldBe (List("Hola"))
  }

  it should """ complext tree """ in {
    val tree: Tree[String] =
      Tree("Auto", List(
        Tree("Puerta", List(Tree("Ventanilla"), Tree("Manija"))),
        Tree("Motor", List(Tree("Cilindro"), Tree("Injector"), Tree("Gujia"))),
        Tree("Rueda", List(Tree("Llanta"), Tree("Cubierta"))),
      ))

    tree.toList.reverse shouldBe
      List("Cubierta", "Llanta", "Rueda", "Gujia", "Injector", "Cilindro", "Motor", "Manija", "Ventanilla", "Puerta", "Auto")

    tree.filterTree(_.contains("o")).toList shouldBe List("Auto", "Motor", "Cilindro", "Injector")

  }

  it should """ concatenate two Tree """ in {
    (Tree("Hola", List(Tree("Que"))) + Tree("Tal")).toList shouldBe List("Hola", "Que", "Tal")
  }

  it should """ concatenate an empty tree """ in {
    (Tree("Hola", List(Tree("Que"))) + Tree.empty).toList shouldBe List("Hola", "Que")
  }

}





