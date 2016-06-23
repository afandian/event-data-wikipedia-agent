(ns event-data-wikipedia-agent.core
  (:require [clojure.tools.logging :as l])
  (:require [event-data-wikipedia-agent.ingest :as ingest]
            [event-data-wikipedia-agent.process :as process]
            [event-data-wikipedia-agent.monitor :as monitor]
            [event-data-wikipedia-agent.push :as push])
  (:require [baleen.context :as baleen]
            [baleen.monitor :as baleen-monitor])
  (:gen-class))

(def config
  #{:recent-changes-subscribe-filter} ; wikipedia recent changes live stream filter. Should be "*" but can be e.g. "en.wikipedia.org"
)

(defn main-ingest [context]
  (l/info "Ingest")
  (baleen-monitor/register-heartbeat context "ingest")
  (ingest/run context))

; NB bear in mind the contention for the Jedis pool.
; Queue processing threads can deadlock each other!
(def num-threads 10)

(defn main-process [context]
  (l/info "Process")
  (baleen-monitor/register-heartbeat context "process")

  (let [threads (map (fn [_] (Thread. (fn [] (process/run context))))
                             (range 0 num-threads))]
    (doseq [thread threads]
      (l/info "Start process on thread" thread)
      (.start ^Thread thread)
      (l/info "Started process on thread" thread))

    ; These should never exit, but stop the process if they do.
    (doseq [thread threads]
      (l/info "Stopped process on thread" thread)
      (.join ^Thread thread))))

(defn main-push [context]
  (l/info "Push")
  (baleen-monitor/register-heartbeat context "push")
  (push/run context))

(defn main-archive [context]
  ; TODO
  (l/info "Archive"))

(defn main-monitor [context]
  (l/info "Monitor")
  (baleen-monitor/register-heartbeat context "monitor")
  (baleen-monitor/run context))

(defn main-unrecognised-action
  [command]
  (l/fatal "ERROR didn't recognise " command))

(defn -main
  [& args]
  (let [context (baleen/->Context
                  "wikipedia"
                  "Wikipedia Event Data Agent"
                  config)

        command (first args)]

    (baleen/boot! context)

    (condp = command
      "ingest" (main-ingest context)
      "process" (main-process context)
      "push" (main-push context)
      "archive" (main-archive context)
      "monitor" (main-monitor context)
      (main-unrecognised-action command))))
