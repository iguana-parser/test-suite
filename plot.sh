#!/usr/local/bin/Rscript

library(extrafont)
loadfonts()

draw_plot <- function(name, displayName, iguana, antlr) {
	output <- paste(name, ".pdf", sep="")
	pdf(output, width=10, height=2.5, family="CM Roman")

	par(mar=c(2, 2, 0.2, 0.2))
	boxplot(iguana$Score, antlr$Score, horizontal=TRUE, boxwex=0.5, outline=FALSE, names=c("Iguana", "ANTLR"), cex=1.5, ylim=c(0,43))
	title(displayName, adj=0.98, line=-1.5)

	text(x = boxplot.stats(round(iguana$Score, 2))$stats[1], labels = boxplot.stats(round(iguana$Score, 2))$stats[1], y = 0.62)
	text(x = boxplot.stats(round(iguana$Score, 2))$stats[3], labels = boxplot.stats(round(iguana$Score, 2))$stats[3], y = 0.62)
	text(x = boxplot.stats(round(iguana$Score, 2))$stats[5], labels = boxplot.stats(round(iguana$Score, 2))$stats[5], y = 0.62)
	
	text(x = boxplot.stats(round(antlr$Score, 2))$stats[1], labels = boxplot.stats(round(antlr$Score, 2))$stats[1], y = 1.62)
	text(x = boxplot.stats(round(antlr$Score, 2))$stats[3], labels = boxplot.stats(round(antlr$Score, 2))$stats[3], y = 1.62)
	text(x = boxplot.stats(round(antlr$Score, 2))$stats[5], labels = boxplot.stats(round(antlr$Score, 2))$stats[5], y = 1.62)

	dev.off()
}

draw_relative_plot <- function(name, displayName, data) {
	output <- paste(name, ".pdf", sep="")
	pdf(output, width=12, height=6, family="CM Roman")

	boxplot(data, horizontal=TRUE, names = c("Relative performance"), outline=FALSE)
	text(x = boxplot.stats(round(data, 2))$stats, labels = boxplot.stats(round(data, 2))$stats, y = 0.55)

	text(x = boxplot.stats(round(data, 2))$stats[1], labels = boxplot.stats(round(data, 2))$stats[1], y = 0.62)
	text(x = boxplot.stats(round(data, 2))$stats[3], labels = boxplot.stats(round(data, 2))$stats[3], y = 0.62)
	text(x = boxplot.stats(round(data, 2))$stats[5], labels = boxplot.stats(round(data, 2))$stats[5], y = 0.62)

	dev.off()
}

all_draw_relative_plot <- function(iguana, antlr) {
	output <- paste("all_relative.pdf", sep="")
	pdf(output, width=8, height=8, family="CM Roman")

	relative <- list();
	for (i in 1:6) {
		relative[[i]] <- iguana[[i]]$Score / antlr[[i]]$Score	
	}

	labels <- c("Junit 4", "Elastic Search", "Guava", "RxJava", "OpenJDK 7", "All Projects")

	par(mar=c(2, 2, 1, 1))
	boxplot(relative[[1]], relative[[2]], relative[[3]], relative[[4]], relative[[5]], relative[[6]], 
		horizontal=TRUE, names=labels, boxwex=0.5, outline=FALSE, cex=1.5)

	for (i in 1:6) {
		text(x = boxplot.stats(round(relative[[i]], 2))$stats[1], labels = boxplot.stats(round(relative[[i]], 2))$stats[1], y = i - 1 + 0.62)
		text(x = boxplot.stats(round(relative[[i]], 2))$stats[3], labels = boxplot.stats(round(relative[[i]], 2))$stats[3], y = i - 1 + 0.62)
		text(x = boxplot.stats(round(relative[[i]], 2))$stats[5], labels = boxplot.stats(round(relative[[i]], 2))$stats[5], y = i - 1 + 0.62)		
	}

	dev.off()
}

names <- c("junit4", "guava", "elasticsearch", "RxJava", "jdk7u-jdk", "total")
displayNames <- c("Junit 4", "Guava", "Elastic Search", "RxJava", "OpenJDK 7", "All Projects")

total_iguana <- data.frame()
total_antlr <- data.frame()

data_iguana <- list()
data_antlr <- list()

for (i in 1:5) {
	name <- names[i]
	displayName <- displayNames[i]

	iguana <- read.csv(paste("Iguana_", name, ".csv", sep=""), header=TRUE, sep=",", dec=",", stringsAsFactors=FALSE)
	iguana[, "Score"]  <- as.numeric(iguana[, "Score"])
	total_iguana <- rbind(total_iguana, iguana)

	antlr <- read.csv(paste("Antlr_", name, ".csv", sep=""), header=TRUE, sep=",", dec=",", stringsAsFactors=FALSE)
	antlr[, "Score"]  <- as.numeric(antlr[, "Score"])
	total_antlr <- rbind(total_antlr, antlr)

	print(length(iguana$Score))
	print(length(antlr$Score))

	data_iguana[[i]] <- iguana
	data_antlr[[i]] <- antlr
}

data_antlr[[6]] <- total_antlr
data_iguana[[6]] <- total_iguana

for (i in 1:length(names)) {
	name <- names[i]
	displayName <- displayNames[i]

	draw_plot(name, displayName, data_iguana[[i]], data_antlr[[i]])
}

for (i in 1:length(names)) {
	name <- names[i]
	displayName <- displayNames[i]

	relative <- data_iguana[[i]]$Score / data_antlr[[i]]$Score
	draw_relative_plot(paste("relative_", name, sep=""), displayName, relative)
}

all_draw_relative_plot(data_iguana, data_antlr)

for (name in names) {
	output <- paste(name, ".pdf", sep="")
	embedded_output <- paste("embedded_", output, sep="")
	embed_fonts(output, outfile=embedded_output)
}

embed_fonts("total.pdf", outfile="embedded_total.pdf")
embed_fonts("all_relative.pdf", outfile="embedded_all_relative.pdf")

