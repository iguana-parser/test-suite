#!/usr/local/bin/Rscript

library(extrafont)
loadfonts()

par(mar = c(4.2,4.2,0.2,0.2))

names <- c("RxJava", "elasticsearch", "jdk7u-jdk", "guava", "junit4")

for (name in names) {
	output <- paste(name, ".pdf", sep="")
	pdf(output, width=12, height=8, family="CM Roman")

	iguana <- read.csv(paste("Iguana_", name, ".csv", sep=""), header=TRUE, sep=",", dec=",", stringsAsFactors=FALSE)
	iguana[, "Score"]  <- as.numeric(iguana[, "Score"])

	antlr <- read.csv(paste("Antlr_", name, ".csv", sep=""), header=TRUE, sep=",", dec=",", stringsAsFactors=FALSE)
	antlr[, "Score"]  <- as.numeric(antlr[, "Score"])

	boxplot(iguana$Score, antlr$Score, horizontal=TRUE, names = c("Iguana", "ANTLR"), las=2, outline=FALSE)
	text(x = boxplot.stats(round(iguana$Score, 2))$stats, labels = boxplot.stats(round(iguana$Score, 2))$stats, y = 0.55)
	text(x = boxplot.stats(round(antlr$Score, 2))$stats, labels = boxplot.stats(round(antlr$Score, 2))$stats, y = 1.55)

	dev.off()
}

for (name in names) {
	output <- paste(name, ".pdf", sep="")
	embedded_output <- paste("embedded_", output, sep="")
	embed_fonts(output, outfile=embedded_output)
}
