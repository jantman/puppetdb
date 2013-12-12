;; ## CLI invokation
;;
;; This is a tiny shim that delegates the command-line arguments to an
;; appropriate handler.
;;
;; If the user executes the program with arguments like so:
;;
;;     ./this-program foo arg1 arg2 arg3
;;
;; ...then we'll look for a namespace called
;; `com.puppetlabs.puppetdb.cli.foo` and invoke its `-main` method with
;; `[arg1 arg2 arg3]`.

(ns com.puppetlabs.puppetdb.core
  (:require [puppetlabs.kitchensink.core :as kitchensink]
            [com.puppetlabs.utils.logging :as logging-utils]
            [clojure.tools.namespace :as ns]
            [clojure.tools.logging :as log]
            [com.puppetlabs.puppetdb.utils :as utils]
            [spyscope.core])
  (:use [clojure.string :only (split)])
  (:gen-class))

(def ns-prefix "com.puppetlabs.puppetdb.cli.")

(defn cli-namespaces
  "Return a set of namespaces underneath the .cli parent"
  []
  {:post [(set? %)]}
  #spy/d (set (for [namespace #spy/d (ns/find-namespaces-on-classpath)
             :let [ns-str (name namespace)]
             :when (.startsWith ns-str ns-prefix)]
         namespace)))

(defn var-from-ns
  "Resolve a var, by name, from a (potentially un-required)
  namespace. If no matching var is found, returns nil.

  Example:

    (var-from-ns \"clojure.string\" \"split\")"
  [ns v]
  {:pre [(string? ns)
         (string? v)]}
  (require (symbol ns))
  (if-let [var (resolve (symbol ns v))]
    (var-get var)))

(defn available-subcommands
  "Return the set of available subcommands for this application"
  []
  {:post [(set? %)]}
  #spy/d (set (for [namespace (cli-namespaces)
             :let [ns-str (name namespace)
                   subcmd (last (split ns-str #"\."))]]
         subcmd)))

(defn usage
  "Display help text to the user"
  []
  (let [cmds #spy/d (sort (for [cmd (available-subcommands)]
                     [cmd (var-from-ns (str ns-prefix cmd) "cli-description")]))]
    (println "Available subcommands:\n")
    (doseq [[subcommand description] cmds]
      (println subcommand "\t" (or description "")))
    (println "\nFor help on a given subcommand, invoke it with -h")))

(defn run-command
  "Does the real work of invoking a command by attempting to result it and
   passing in args. `success-fn` is a no-arg function that is called when the
   command successfully executes.  `fail-fn` is called when a bad command is given
   or a failure executing a command."
  [success-fn fail-fn args]
  (let [subcommand (first args)
        allowed?   (available-subcommands)]

    (utils/alert-deprecated-jdk)

    ;; Bad invokation
    (when-not #spy/d (allowed? subcommand)
      (usage)
      (fail-fn))

    (let [module (str ns-prefix subcommand)
          args   (rest args)]
      (try
        (require (symbol module))
        (apply (resolve (symbol module "-main")) args)
        (success-fn)
        (catch Throwable e
          (logging-utils/catch-all-logger e)
          (fail-fn))))))

(defn -main
  [& args]
  (run-command #(System/exit 0) #(System/exit 1) args))
