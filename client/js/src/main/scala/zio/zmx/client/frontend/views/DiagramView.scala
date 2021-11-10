package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._
import zio.zmx.client.MetricsMessage

import zio.zmx.client.frontend.icons.HeroIcon.SolidIcon._
import zio.zmx.client.frontend.utils.Modifiers._

import zio.zmx.client.frontend.model._
import zio.zmx.client.frontend.state._

/**
 * A DiagramView is implemented as a Laminar element and is responsible for initializing and updating
 * the graph(s) embedded within. It taps into the overall stream of change events, filters out the events
 * that are relevant for the diagram and updates the TimeSeries of the underlying Charts.
 *
 * As we might see a lot of change events, we will throttle the update interval for the graphs as specified in
 * the individual diagram config.
 */
object DiagramView {

  def render(id: String, initial: (DiagramConfig, Int), $config: Signal[(DiagramConfig, Int)]): HtmlElement =
    new DiagramViewImpl($config, AppState.messages.events).render()

  private class DiagramViewImpl(
    $cfg: Signal[(DiagramConfig, Int)],
    events: EventStream[MetricsMessage]
  ) {

    // A Chart element that will be unitialised and can be inserted into the dom by calling
    // the element() method
    private val chart: ChartView.ChartView = ChartView.ChartView()

    private val titleVar = Var("")

    def diagramControls(d: DiagramConfig, count: Int): HtmlElement =
      div(
        cls := "w-1/5 flex justify-end",
        a(
          cls := "rounded text-center place-self-center h-10 w-10 text-white",
          displayWhen($cfg.map(_._1.displayIndex > 0)),
          arrowUp(svg.className := "h-full w-full"),
          onClick.map(_ => Command.MoveDiagram(d, Direction.Up)) --> Command.observer
        ),
        a(
          cls := "rounded text-center place-self-center h-10 w-10 text-white",
          displayWhen($cfg.map { case (diag, c) => diag.displayIndex < c - 1 }),
          arrowDown(svg.className := "h-full w-full"),
          onClick.map(_ => Command.MoveDiagram(d, Direction.Down)) --> Command.observer
        ),
        a(
          cls := "rounded text-center place-self-center h-10 w-10 text-red-500",
          close(svg.className := "h-full w-full"),
          onClick.map(_ => Command.RemoveDiagram(d)) --> Command.observer
        )
      )

    def chartConfig(d: DiagramConfig): HtmlElement =
      div(
        cls := "w-full h-2/3 flex flex-col justify-between",
        form(
          cls := "h-full",
          onSubmit.preventDefault.mapTo(
            Command.UpdateDiagram(d.copy(title = titleVar.now()))
          ) --> Command.observer,
          p(
            cls := "flex",
            label(cls := "w-1/3 text-gray-50 text-xl font-bold", "Title: "),
            input(
              cls := "w-2/3 rounded-xl px-3 text-gray-600",
              placeholder(s"Enter Diagram title e.g:${d.title}"),
              controlled(
                value <-- titleVar,
                onInput.mapToValue --> titleVar
              )
            )
          ),
          button(
            cls := "bg-blue-500 hover:bg-blue-700 text-white font-bold py-1 px-4 rounded text-center place-self-center",
            typ("submit"),
            "Submit"
          )
        )
      )

    def render(): HtmlElement =
      div(
        child <-- $cfg.map { cfg =>
          div(
            events
              .filter(m => cfg._1.metric.contains(m.key))
              .throttle(cfg._1.refresh.toMillis().intValue()) --> Observer[MetricsMessage](onNext = { msg =>
              TimeSeriesEntry.fromMetricsMessage(msg).foreach(chart.recordData)
              chart.update()
            }),
            cls := "bg-gray-900 text-gray-50 rounded my-3 p-3",
            div(
              cls := "w-full flex",
              span(
                cls := "w-4/5 items-center text-2xl font-bold my-2",
                cfg._1.title
              ),
              diagramControls(cfg._1, cfg._2)
            ),
            div(
              cls := "flex",
              chart.element(),
              div(
                cls := "w-1/5 my-3 p-3 flex flex-col justify-between",
                chartConfig(cfg._1)
              )
            )
          )
        }
      )
  }
}
