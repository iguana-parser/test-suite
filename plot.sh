#!/usr/local/bin/Rscript

Sys.setenv(LANG = "en")

args<-commandArgs(TRUE)

names <- c("RxJava", "elasticsearch", "jdk7u-jdk", "guava", "junit4")

for (name in names) {

	iguana <- read.csv(paste("Iguana_", name, ".csv", sep=""), header=TRUE, sep=",", dec=",", stringsAsFactors=FALSE)
	iguana[, "Score"]  <- as.numeric(iguana[, "Score"])

	antlr <- read.csv(paste("Antlr_", name, ".csv", sep=""), header=TRUE, sep=",", dec=",", stringsAsFactors=FALSE)
	antlr[, "Score"]  <- as.numeric(antlr[, "Score"])

	pdf(paste(name, ".pdf", sep=""), width=12, height=8)

	boxplot(iguana$Score, antlr$Score, horizontal=TRUE, names = c("Iguana", "Antlr"), las=2, outline=FALSE)
	text(x = boxplot.stats(round(iguana$Score, 2))$stats, labels = boxplot.stats(round(iguana$Score, 2))$stats, y = 0.55)
	text(x = boxplot.stats(round(antlr$Score, 2))$stats, labels = boxplot.stats(round(antlr$Score, 2))$stats, y = 1.55)

}

dev.off()
