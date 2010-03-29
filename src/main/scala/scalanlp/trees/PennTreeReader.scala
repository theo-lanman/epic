package scalanlp.trees;
/*
 Copyright 2010 David Hall

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/


import scala.util.parsing.combinator._
import scala.util.parsing.combinator.syntactical._
import scala.util.parsing.combinator.lexical._;
import scala.util.parsing.input._;

import scala.io.Source;

class PennTreeReader(badLabels: Set[String]) extends StdLexical with ImplicitConversions with Scanners {
  def this() = this(Set("-NONE-"));
  private val ws = whitespace;
  private val other = acceptIf( c => !c.isWhitespace && c != '(' && c != ')')( "'" + _ + "'not expected");
  private val rparen = (ws ~> accept(')')) <~ ws;
  private val lparen = (ws ~> accept('(')) <~ ws;

  private val tok = rep(other) ^^ { x => x.mkString("")};

  private def seqTree(pos:Int):Parser[(List[Tree[String]],Seq[String])] = (
    tree(pos) >> {
      case Some((tree,words)) => 
        seqTree(pos+words.length).? ^^ { 
          case Some( (restTrees,restWords) ) => 
            (tree :: restTrees, words.toList ++ restWords.toList)
          case None =>(tree :: Nil, words)
        }
      case None =>
        seqTree(pos).? ^^ { 
          case None => (Nil,Nil)
          case Some( x ) => x
        }
    }
  )

  private def tree(pos:Int):Parser[Option[(Tree[String],Seq[String])]] = ( 
   ( (lparen ~> tok <~ ws) ~ tok <~ rparen ^^ {
      case (label ~ word) if badLabels(label) => None
      case (label ~ word) => Some ((Tree(label,Seq())(Span(pos,pos+1)),Seq(word)) )
    })
    |(lparen ~> opt(tok) ) ~ (seqTree(pos) <~ rparen) ^^ {
      case (Some("-NONE-") ~ _ ) => None
      case (mbLabel ~ children) =>
        val words = children._2;
        Some((Tree(mbLabel.getOrElse(""),children._1)(Span(pos,pos + words.length)), words))
    }
  )

  def readTrees(input: String): Either[List[(Tree[String],Seq[String])],ParseResult[List[(Tree[String],Seq[String])]]] = {
    phrase(rep1(tree(0)))(new CharSequenceReader(input)) match {
        case Success( result, _) => Left( result map (_.get) )
        case x => Right(x map (_ map (_.get)));
      }
  }


  def readTree(input: String): Either[(Tree[String],Seq[String]),ParseResult[(Tree[String],Seq[String])]] = {
      phrase(tree(0))(new CharSequenceReader(input)) match {
        case Success( result, _) => Left( result.get )
        case x => Right(x map (_.get));
      }
  }

}
