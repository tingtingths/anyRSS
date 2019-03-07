package me.itdog.rssthis;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.util.Arrays;
import java.util.function.BinaryOperator;
import java.util.function.Function;

@SpringBootApplication
@EnableSwagger2
public class RssThisApplication {

	public static void main(String[] args) {
		//SpringApplication.run(RssThisApplication.class, args);
		test();
	}

	private static void test() {
		// with lambda
		Integer squareSum = Arrays.asList(1, 2, 3, 4, 5)
				.stream()
				.map((x) -> x * x)
				.reduce((x, acc) -> acc += x)
				.get();
		System.out.println(squareSum); // 55

		// without lambda
		squareSum = Arrays.asList(1, 2, 3, 4, 5)
				.stream()
				.map(new Function<Integer, Integer>() {
					@Override
					public Integer apply(Integer x) {
						return x * x;
					}
				})
				.reduce(new BinaryOperator<Integer>() {
					@Override
					public Integer apply(Integer x, Integer acc) {
						return acc += x;
					}
				})
				.get();
		System.out.println(squareSum); // 55
	}
}
