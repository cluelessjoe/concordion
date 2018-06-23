package org.concordion.internal;

import org.concordion.Concordion;
import org.concordion.api.Fixture;
import org.concordion.api.FixtureDeclarations;
import org.concordion.api.ResultSummary;
import org.concordion.api.SpecificationLocator;
import org.concordion.internal.cache.RunResultsCache;
import org.concordion.internal.extension.FixtureExtensionLoader;

import java.io.IOException;

public class FixtureRunner {
    private static RunResultsCache runResultsCache = RunResultsCache.SINGLETON;
    private Concordion concordion;

    public FixtureRunner(Fixture fixture, SpecificationLocator specificationLocator) throws UnableToBuildConcordionException {
        ConcordionBuilder concordionBuilder = new ConcordionBuilder().withFixtureType(fixture.getFixtureType()).withSpecificationLocator(specificationLocator);
        new FixtureExtensionLoader().addExtensions(fixture, concordionBuilder);
        new FixtureOptionsLoader().addOptions(fixture.getFixtureType(), concordionBuilder);
        concordion = concordionBuilder.build();
    }

    public ResultSummary run(String example, Fixture fixture) throws IOException {

        FixtureType fixtureType = fixture.getFixtureType();

        RunOutput runOutput = runResultsCache.startRun(fixtureType, example);
        ResultSummary actualResultSummary = runOutput==null?
                null:
                runOutput.getActualResultSummary();

        String additionalInformation = null;
    	if (runOutput == null) {

            try {
                ImplementationStatusChecker statusChecker;
                if (example != null) {
                    fixture.beforeProcessExample(example);
                    try {
                        actualResultSummary = concordion.processExample(fixture, example);
                        statusChecker = ImplementationStatusChecker.getImplementationStatusChecker(
                                fixtureType, actualResultSummary.getImplementationStatus());
                    } finally {
                        fixture.afterProcessExample(example);
                    }
                } else {
                    actualResultSummary = concordion.process(fixture);
                    statusChecker = ImplementationStatusChecker.getImplementationStatusChecker(
                            fixtureType, null);
                }

                runResultsCache.finishRun(
                        fixtureType, example,
                        actualResultSummary,
                        statusChecker);

            } catch (RuntimeException e) {
                // the run failed miserably. Tell the cache that the run failed
                runResultsCache.failRun(fixtureType, example);
                throw e;
            }

        } else {
            additionalInformation = "\nFrom cache: ";
        }

        if (actualResultSummary.isForExample()) {
            printResultSummary(fixtureType, example, actualResultSummary, additionalInformation);
        }

        return actualResultSummary;
    }

    private void printResultSummary(FixtureDeclarations fixtureDeclarations, String example, ResultSummary resultSummary, String additionalInformation) {
        synchronized (System.out) {
            if (additionalInformation != null) {
                System.out.print(additionalInformation);
            }
            resultSummary.print(System.out, fixtureDeclarations);
            resultSummary.assertIsSatisfied(fixtureDeclarations);
        }
    }

    public synchronized Concordion getConcordion() {
        return concordion;
    }

    /**
     *
     * This method is deprecated as it's only used in jUnit 3.
     *
     * @param fixture fixture
     * @return result summary
     * @throws IOException on io error
     */
    @Deprecated
    public ResultSummary run(Fixture fixture) throws IOException {
        RunOutput results = RunResultsCache.SINGLETON.getFromCache(fixture.getFixtureType(), null);

        ResultSummary resultSummary = run(null, fixture);

        // only actually finish the specification if it has not already been run.
        if (results == null) {
            concordion.finish();
        }
        resultSummary.print(System.out, fixture.getFixtureType());
        return resultSummary;
    }
}
