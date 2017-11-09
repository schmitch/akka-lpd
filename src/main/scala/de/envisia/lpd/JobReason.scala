package de.envisia.lpd

sealed trait JobReason

object JobReason {

  // Defined in JobMon RFC
  // https://tools.ietf.org/html/rfc2707#section-3.3.9.1

  case object Other extends JobReason
  case object Unknown extends JobReason
  case object JobIncoming extends JobReason
  case object SubmissionInterrupted extends JobReason
  case object JobOutgoing extends JobReason
  case object JobHoldSpecified extends JobReason
  case object JobHoldUntilSpecified extends JobReason
  case object JobProcessAfterSpecified extends JobReason
  case object ResourcesAreNotReady extends JobReason
  case object DeviceStoppedPartly extends JobReason
  case object DeviceStopped extends JobReason
  case object JobInterpreting extends JobReason
  case object JobPrinting extends JobReason
  case object JobCanceledByUser extends JobReason
  case object JobCanceledByOperator extends JobReason
  case object JobCanceledAtDevice extends JobReason
  case object AbortedBySystem extends JobReason
  case object ProcessingToStopPoint extends JobReason
  case object ServiceOffLine extends JobReason
  case object JobCompletedSuccessfully extends JobReason
  case object JobCompletedWithWarnings extends JobReason
  case object JobCompletedWithErrors extends JobReason

  def fromInt(value: Int): JobReason = {
    value match {
      case 0x1 => Other
      case 0x2 => Unknown
      case 0x4 => JobIncoming
      case 0x8 => SubmissionInterrupted
      case 0x10 => JobOutgoing
      case 0x20 => JobHoldSpecified
      case 0x40 => JobHoldUntilSpecified
      case 0x80 => JobProcessAfterSpecified
      case 0x100 => ResourcesAreNotReady
      case 0x200 => DeviceStoppedPartly
      case 0x400 => DeviceStopped
      case 0x800 => JobInterpreting
      case 0x1000 => JobPrinting
      case 0x2000 => JobCanceledByUser
      case 0x4000 => JobCanceledByOperator
      case 0x8000 => JobCanceledAtDevice
      case 0x10000 => AbortedBySystem
      case 0x20000 => ProcessingToStopPoint
      case 0x40000 => ServiceOffLine
      case 0x80000 => JobCompletedSuccessfully
      case 0x100000 => JobCompletedWithWarnings
      case 0x200000 => JobCompletedWithErrors
      case _ => throw new IllegalStateException("invalid job reason")
    }
  }

}

