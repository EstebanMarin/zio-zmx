package zio.zmx.client.frontend.state

import com.raquo.airstream.core.Observer
import zio.zmx.client.frontend.model.DiagramConfig
import zio.zmx.client.MetricsMessage
import zio.zmx.client.frontend.model.MetricSummary

sealed trait Command

object Command {

  case object Disconnect                               extends Command
  final case class Connect(url: String)                extends Command
  final case class AddDiagram(cfg: DiagramConfig)      extends Command
  final case class RecordData(msg: MetricsMessage)     extends Command
  final case class UpdateDiagram(upCfg: DiagramConfig) extends Command

  val observer = Observer[Command] {
    case Disconnect =>
      println("Disconnecting from server")
      AppState.resetState()

    case Connect(url) =>
      println(s"Connecting url to : [$url]")
      AppState.shouldConnect.set(true)
      AppState.dashboardConfig.update(_.copy(connectUrl = url))

    case AddDiagram(d) =>
      AppState.dashboardConfig.update(cfg =>
        cfg.copy(diagrams = cfg.diagrams :+ d.copy(displayIndex = cfg.diagrams.size))
      )

    case UpdateDiagram(d) =>
      AppState.dashboardConfig.update(cfg =>
        cfg.copy(diagrams = (cfg.diagrams.filter(_.id.equals(d.id)) :+ d).sortBy(_.displayIndex))
      )
    // case AddDiagram(d) => AppState.dashboardConfig.update(cfg => cfg.copy(diagrams = cfg.diagrams :+ d))

    // case UpdateDiagram(d) =>
    //   // create updated Diagram same ID but with updated title
    //   AppState.dashboardConfig.update { cfg =>
    //     // diagrams to be updated
    //     //remove this method
    //     // val updatedDiagram = DiagramConfig(id = d.id, title = title, metric = d.metric, refresh = d.refresh)
    //     val discartedValue = cfg.diagrams.filter(dc => dc.id != d.id)
    //     cfg.copy(diagrams = (discartedValue :+ d).sortedBy(_.displayIndex))
    //   }

    case RecordData(msg) =>
      MetricSummary.fromMessage(msg) match {
        case None    => // do nothing
        case Some(s) =>
          s match {
            case info: MetricSummary.CounterInfo   => AppState.counterInfos.update(_.updated(info.metric, info))
            case info: MetricSummary.GaugeInfo     => AppState.gaugeInfos.update(_.updated(info.metric, info))
            case info: MetricSummary.HistogramInfo => AppState.histogramInfos.update(_.updated(info.metric, info))
            case info: MetricSummary.SummaryInfo   => AppState.summaryInfos.update(_.updated(info.metric, info))
            case info: MetricSummary.SetInfo       => AppState.setCountInfos.update(_.updated(info.metric, info))
          }
      }

  }
}
