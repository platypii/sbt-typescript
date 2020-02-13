package controllers

import com.google.inject.Inject
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

class Application @Inject() (cc: ControllerComponents) extends AbstractController(cc) {

  def index = Action {
    Ok(views.html.index())
  }

}
